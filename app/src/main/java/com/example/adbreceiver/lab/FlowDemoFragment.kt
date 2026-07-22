package com.example.adbreceiver.lab

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.adbreceiver.R
import com.example.adbreceiver.databinding.FragmentFlowDemoBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch

/**
 * DEMO 3 - FLOW. Tre usi tipici, logica tutta qui dentro.
 *
 * Un Flow e' una "sequenza asincrona": produce valori nel tempo, e chi li consuma con
 * collect{} si sospende in attesa del prossimo, senza bloccare il thread. Le tre varianti:
 *
 *  - flow{}        COLD  : non fa nulla finche' non lo raccogli; ogni collect riparte da zero.
 *  - StateFlow     HOT   : un valore osservabile sempre presente (ideale per lo stato di UI).
 *  - operatori     debounce/filter/map: si compongono a catena come su una lista, ma nel tempo.
 */
class FlowDemoFragment : Fragment() {

    private var _binding: FragmentFlowDemoBinding? = null
    private val binding get() = _binding!!

    // Lo stato: un HOT flow che conserva sempre il valore corrente (qui, un contatore).
    private val count = MutableStateFlow(0)

    // La sorgente di ricerca: ogni tasto premuto emette qui dentro.
    private val query = MutableStateFlow("")

    // Bus di EVENTI: a differenza di StateFlow non ha un valore iniziale ne' "corrente".
    // replay=0 -> chi si iscrive dopo l'emissione non rivede gli eventi passati.
    private val events = MutableSharedFlow<Int>(replay = 0)
    private var eventCounter = 0

    // Le demo "live" (9 e 10) girano finche' non le fermiamo: teniamo i Job per farlo.
    private var sampleJob: Job? = null
    private var backpressureJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlowDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- 1. COLD FLOW: prende i numeri 1..6, tiene i pari, li raddoppia. --------------
        // La catena map/filter e' pigra: nulla parte finche' non c'e' un collect{}.
        binding.buttonCold.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val out = mutableListOf<Int>()
                flow {                          // sorgente cold: emette 1,2,3,4,5,6
                    for (n in 1..6) emit(n)
                }
                    .filter { it % 2 == 0 }     // tiene solo i pari
                    .map { it * 2 }             // li raddoppia
                    .collect { out.add(it); binding.textviewCold.text = "raccolti: $out" }
            }
        }

        // --- 2. STATEFLOW: lo osservo una volta, si aggiorna da solo a ogni cambio. --------
        // collect{} qui NON termina mai: resta in ascolto finche' vive la View.
        // repeatOnLifecycle non serve qui perche' lifecycleScope della View si cancella
        // gia' in onDestroyView; per un StateFlow di UI e' sufficiente.
        viewLifecycleOwner.lifecycleScope.launch {
            count.collect { binding.textviewState.text = it.toString() }
        }
        binding.buttonPlus.setOnClickListener {
            count.update { it + 1 }             // update{} = leggi-e-scrivi atomico, senza lock
        }

        // --- 3. DEBOUNCE: reagisco solo quando l'utente smette di digitare per 400ms. ------
        binding.edittextSearch.doAfterTextChanged { query.value = it?.toString().orEmpty() }
        viewLifecycleOwner.lifecycleScope.launch {
            query
                .debounce(400)                  // ignora le raffiche di tasti troppo ravvicinate
                .map { it.trim() }
                .distinctUntilChanged()         // se il testo "utile" non cambia, non rifare nulla
                .filter { it.length >= 2 }      // niente ricerche per 0-1 caratteri
                .collect { binding.textviewSearch.text = "cerco: \"$it\"" }
        }

        // --- 4. flatMapLatest: stessa query, ma ora ogni ricerca e' asincrona. -------------
        // flatMapLatest ANNULLA la lookup precedente quando arriva una query nuova: non vedrai
        // mai il risultato di una ricerca ormai superata. catch{} cattura le eccezioni emesse
        // a monte e le trasforma in un valore, invece di far terminare (o crashare) il flow.
        viewLifecycleOwner.lifecycleScope.launch {
            query
                .debounce(400)
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { q -> fakeSearch(q) }   // ritorna un Flow: lo "appiattisce"
                .catch { e -> emit("errore: ${e.message}") }
                .collect { binding.textviewFml.text = it }
        }

        // --- 5. combine: due flow diventano uno. Riparte a ogni cambiamento di UNO dei due. --
        viewLifecycleOwner.lifecycleScope.launch {
            combine(count, query) { c, q -> "count=$c | query=\"$q\"" }
                .collect { binding.textviewCombine.text = it }
        }

        // --- 6. SharedFlow: eventi one-shot. Il collector deve gia' ascoltare quando parte. --
        viewLifecycleOwner.lifecycleScope.launch {
            events.collect { binding.textviewShared.text = "evento #$it ricevuto" }
        }
        binding.buttonEmitEvent.setOnClickListener {
            // emit e' suspend (potrebbe attendere spazio nel buffer): serve una coroutine.
            viewLifecycleOwner.lifecycleScope.launch { events.emit(++eventCounter) }
        }

        // --- 7. callbackFlow: un CountDownTimer (API a callback) esposto come Flow. ----------
        binding.buttonStartTimer.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                countdownFlow(seconds = 5).collect { binding.textviewCallback.text = it.toString() }
            }
        }

        // --- 8. zip: aspetta una coppia da ENTRAMBI, poi la emette. Diverso da combine, che --
        // scatta appena UNO dei due cambia. zip si ferma quando il flow piu' corto finisce.
        binding.buttonZip.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val numbers = flow { for (n in 1..3) { delay(100); emit(n) } }
                val letters = flow { for (c in listOf("A", "B", "C")) { delay(250); emit(c) } }
                val out = mutableListOf<String>()
                // Ogni coppia esce al ritmo del piu' LENTO dei due (qui 250ms): zip li sincronizza.
                numbers.zip(letters) { n, l -> "$n$l" }
                    .collect { out.add(it); binding.textviewZip.text = "coppie: $out" }
            }
        }

        // --- 9. conflate: avvia/ferma la demo. ---------------------------------------------
        binding.buttonBackpressure.setOnClickListener { toggleConflateDemo() }

        // --- 10. sample: avvia/ferma la sorgente veloce. -----------------------------------
        binding.buttonSample.setOnClickListener { toggleSampleDemo() }
    }

    // --- 9. conflate: la sorgente emette 1..15 (uno ogni 150ms). Il consumatore ci mette ---
    // 600ms a "digerire" ogni valore: mentre e' occupato, conflate NON accoda gli arrivi ma
    // sovrascrive tenendo solo l'ultimo. Cosi' i "grezzi" crescono fitti, ma i "recuperati"
    // saltano i valori intermedi (es. 1, poi 5, poi 9...). Chi decide il ritmo e' il
    // consumatore: emette il prossimo appena torna libero (diverso da sample, guidato dal tempo).
    private fun toggleConflateDemo() {
        backpressureJob?.let {
            it.cancel()
            backpressureJob = null
            binding.buttonBackpressure.text = getString(R.string.flow_button_backpressure)
            return
        }
        val raw = mutableListOf<Int>()
        val recovered = mutableListOf<Int>()
        binding.textviewBackpressureRaw.text = "grezzi emessi: []"
        binding.textviewBackpressure.text = "recuperati (conflate): []"
        binding.buttonBackpressure.text = getString(R.string.flow_button_sample_stop)

        backpressureJob = viewLifecycleOwner.lifecycleScope.launch {
            flow {
                for (n in 1..15) { emit(n); delay(150) }   // produttore VELOCE
            }
                .onEach { raw.add(it); binding.textviewBackpressureRaw.text = "grezzi emessi: $raw" }
                .conflate()                                 // <-- mentre il collector e' occupato, tiene l'ultimo
                .collect {
                    delay(600)                              // consumatore LENTO
                    recovered.add(it)
                    binding.textviewBackpressure.text = "recuperati (conflate): $recovered"
                }
            binding.buttonBackpressure.text = getString(R.string.flow_button_backpressure)
            backpressureJob = null
        }
    }

    // --- 10. sample: un evento spara tanti output al secondo; ne restituiamo l'ultimo -------
    // ogni secondo. sample(1000) campiona a intervalli FISSI e scarta tutto cio' che e'
    // arrivato nel mezzo. (conflate scarta in base alla lentezza del consumatore; debounce
    // aspetta una pausa; sample e' l'unico legato a un intervallo di tempo regolare.)
    @OptIn(FlowPreview::class)
    private fun toggleSampleDemo() {
        // Secondo click -> fermo.
        sampleJob?.let {
            it.cancel()
            sampleJob = null
            binding.buttonSample.text = getString(R.string.flow_button_sample_start)
            return
        }
        binding.buttonSample.text = getString(R.string.flow_button_sample_stop)
        sampleJob = viewLifecycleOwner.lifecycleScope.launch {
            // Sorgente "impazzita": un valore ogni 50ms, cioe' ~20 al secondo, all'infinito.
            val firehose = flow {
                var i = 0
                while (true) { emit(++i); delay(50) }
            }
            firehose
                .onEach { binding.textviewSampleRaw.text = "grezzi emessi: $it" }  // ~20/sec
                .sample(1000)                                                      // 1/sec: l'ultimo
                .collect { binding.textviewSample.text = it.toString() }
        }
    }

    /** Finta chiamata di rete: si sospende 600ms, poi restituisce un risultato (o lancia). */
    private fun fakeSearch(q: String) = flow {
        if (q.length < 2) { emit("—"); return@flow }
        emit("cerco \"$q\"…")
        delay(600)                          // se arriva una query nuova, flatMapLatest annulla qui
        if (q == "errore") error("boom simulato")
        emit("trovati 42 risultati per \"$q\"")
    }

    /**
     * Ponte callback -> Flow. callbackFlow crea un canale in cui il callback fa trySend;
     * awaitClose SOSPENDE finche' il collector e' vivo e, quando smette, esegue la pulizia
     * (qui cancel del timer). E' lo stesso schema per sensori, location, listener Android.
     */
    private fun countdownFlow(seconds: Int) = callbackFlow {
        val timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(msLeft: Long) { trySend((msLeft / 1000).toInt() + 1) }
            override fun onFinish() { trySend(0); close() }   // close = fine del flow
        }
        timer.start()
        awaitClose { timer.cancel() }       // chiamato quando il flow viene cancellato/chiuso
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
