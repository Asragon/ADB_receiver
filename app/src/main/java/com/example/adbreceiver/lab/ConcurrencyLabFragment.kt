package com.example.adbreceiver.lab

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.adbreceiver.databinding.FragmentConcurrencyLabBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ConcurrencyLab"

/** Quanti lavoratori partono insieme e quanti incrementi fa ognuno. */
private const val WORKERS = 10
private const val INCREMENTS_PER_WORKER = 10
private const val STEP_DELAY_MS = 20L

/**
 * Schermata-laboratorio: serve solo a imparare, non fa parte del funzionamento dell'app.
 *
 * Ogni pulsante e' un esperimento a se'. Il metodo consigliato: premi prima la versione
 * ROTTA, guarda cosa succede, poi la versione CORRETTA e confronta.
 */
class ConcurrencyLabFragment : Fragment() {

    private var _binding: FragmentConcurrencyLabBinding? = null
    private val binding get() = _binding!!

    private val counter = SharedCounter()
    private val logLines = mutableListOf<String>()

    /**
     * Teniamo il riferimento al lavoro in corso per non far partire due demo sovrapposte
     * (che confonderebbero le idee) e per poterlo annullare.
     */
    private var currentJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConcurrencyLabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- DEMO 1: MUTEX -------------------------------------------------------------
        binding.buttonRunUnsafe.setOnClickListener {
            runCounterDemo(safe = false)
        }
        binding.buttonRunSafe.setOnClickListener {
            runCounterDemo(safe = true)
        }
        binding.buttonProofUiAlive.setOnClickListener {
            // Premilo mentre la demo gira: risponde all'istante. Se avessimo usato
            // synchronized/ReentrantLock al posto del Mutex, qui l'app sarebbe congelata.
            Toast.makeText(requireContext(), "UI viva e reattiva!", Toast.LENGTH_SHORT).show()
        }

        // --- DEMO 2: COMPLETABLE DEFERRED ----------------------------------------------
        binding.buttonRunWizard.setOnClickListener { runWizard() }
        binding.buttonTwoDialogsUnsafe.setOnClickListener { runTwoDialogs(gated = false) }
        binding.buttonTwoDialogsSafe.setOnClickListener { runTwoDialogs(gated = true) }

        binding.buttonClearLog.setOnClickListener {
            logLines.clear()
            renderLog()
        }

        renderCounter(expected = 0, finished = false)
    }

    // =====================================================================================
    // DEMO 1 - MUTEX: il contatore condiviso
    // =====================================================================================

    private fun runCounterDemo(safe: Boolean) {
        if (currentJob?.isActive == true) {
            appendLog("una demo e' gia' in corso, aspetta che finisca")
            return
        }
        counter.reset()
        val expected = WORKERS * INCREMENTS_PER_WORKER
        appendLog(if (safe) "contatore CON mutex: parto" else "contatore SENZA mutex: parto")

        // viewLifecycleOwner: il lavoro viene cancellato da solo quando la View muore.
        // Usare lifecycleScope del Fragment (senza "viewLifecycleOwner") sarebbe un bug
        // classico: la coroutine sopravviverebbe alla View e toccherebbe binding gia' nulli.
        currentJob = viewLifecycleOwner.lifecycleScope.launch {
            // launch{} qui NON crea thread: tutte queste coroutine girano sul main thread.
            // Il conteggio sbagliera' lo stesso, ed e' proprio questo il punto della demo.
            val workers = List(WORKERS) { workerIndex ->
                launch {
                    repeat(INCREMENTS_PER_WORKER) {
                        if (safe) {
                            counter.incrementSafe(STEP_DELAY_MS)
                        } else {
                            counter.incrementUnsafe(STEP_DELAY_MS)
                        }
                        renderCounter(expected, finished = false)
                    }
                    Log.v(TAG, "worker $workerIndex finito")
                }
            }
            workers.forEach { it.join() }   // aspetto che tutti abbiano finito

            renderCounter(expected, finished = true)
            val lost = expected - counter.value
            appendLog(
                if (lost == 0) "risultato ${counter.value}/$expected - nessun incremento perso"
                else "risultato ${counter.value}/$expected - PERSI $lost incrementi"
            )
        }
    }

    private fun renderCounter(expected: Int, finished: Boolean) {
        val b = _binding ?: return
        b.textviewCounter.text = "${counter.value} / $expected"
        b.textviewCounterVerdict.text = when {
            !finished -> "in corso... dentro la sezione critica: ${counter.insideCriticalSection}"
            counter.value == expected -> "Corretto: ogni incremento e' stato conservato."
            else -> "Persi ${expected - counter.value} incrementi su $expected " +
                    "(lost update: tutti leggono lo stesso valore vecchio)."
        }
    }

    // =====================================================================================
    // DEMO 2 - COMPLETABLE DEFERRED: dialog che restituiscono un valore
    // =====================================================================================

    /**
     * Tre domande in fila. Guarda com'e' scritto: sembra codice sincrono, riga dopo riga,
     * con le risposte in normali "val". Con i listener annidati sarebbero tre livelli di
     * callback e le variabili andrebbero portate a mano da un livello all'altro.
     */
    private fun runWizard() {
        currentJob = viewLifecycleOwner.lifecycleScope.launch {
            appendLog("wizard: inizio")

            val likesKotlin = askUser(requireContext(), "1 di 3", "Ti piace Kotlin?")
            appendLog("wizard: risposta 1 = $likesKotlin")

            // Il flusso puo' ramificare come un normale if: impossibile da leggere
            // altrettanto bene con i callback.
            if (likesKotlin != UserAnswer.YES) {
                appendLog("wizard: interrotto dopo la prima risposta")
                return@launch
            }

            val second = askUser(requireContext(), "2 di 3", "Hai capito il CompletableDeferred?")
            appendLog("wizard: risposta 2 = $second")

            val third = askUser(requireContext(), "3 di 3", "Passiamo al prossimo concetto?")
            appendLog("wizard: risposta 3 = $third")
            appendLog("wizard: fine")
        }
    }

    /**
     * Due flussi indipendenti che vogliono parlare all'utente nello stesso momento.
     *
     * gated = false -> i dialog si sovrappongono, ne vedi uno solo e l'altro e' nascosto sotto.
     * gated = true  -> il Mutex li mette in fila: rispondi al primo, poi appare il secondo.
     */
    private fun runTwoDialogs(gated: Boolean) {
        currentJob = viewLifecycleOwner.lifecycleScope.launch {
            appendLog(if (gated) "due dialog CON gate" else "due dialog SENZA gate")

            val first = launch {
                val a = if (gated) DialogGate.ask(requireContext(), "Flusso A", "Confermi A?")
                else askUser(requireContext(), "Flusso A", "Confermi A?")
                appendLog("flusso A -> $a")
            }
            val second = launch {
                delay(100L)   // arriva un attimo dopo, come farebbe un evento reale
                val b = if (gated) DialogGate.ask(requireContext(), "Flusso B", "Confermi B?")
                else askUser(requireContext(), "Flusso B", "Confermi B?")
                appendLog("flusso B -> $b")
            }
            first.join()
            second.join()
            appendLog("entrambi i flussi conclusi")
        }
    }

    // =====================================================================================

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, message)
        logLines.add("$time  $message")
        if (logLines.size > 20) logLines.removeAt(0)
        renderLog()
    }

    private fun renderLog() {
        val b = _binding ?: return
        b.textviewLog.text = if (logLines.isEmpty()) "(log vuoto)" else logLines.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Non serve cancellare currentJob a mano: viewLifecycleOwner.lifecycleScope lo fa
        // gia' lui. Lo azzeriamo solo per non tenere un riferimento inutile.
        currentJob = null
        _binding = null
    }
}
