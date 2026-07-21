package com.example.adbreceiver.lab

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AwaitableDialog"

/** La risposta dell'utente, come valore di ritorno invece che come callback. */
enum class UserAnswer { YES, NO, DISMISSED }

/**
 * DEMO 2 - COMPLETABLE DEFERRED.
 *
 * Il problema: un AlertDialog e' un'API a callback. Non "restituisce" nulla; ti richiama
 * piu' tardi. Per fare tre domande di fila devi annidare i listener uno dentro l'altro
 * (la famosa "callback hell"), e il flusso del programma si spezza in frammenti.
 *
 * CompletableDeferred<T> e' una promessa vuota: chi la aspetta si sospende su await(),
 * e il listener del bottone la riempie con complete(valore). Cosi' il dialog diventa una
 * normale funzione suspend che RITORNA la scelta, e il tuo codice torna a leggersi
 * dall'alto verso il basso.
 *
 * E' il ponte canonico "mondo a callback -> mondo coroutine". Serve ogni volta che
 * chi produce il risultato NON e' chi lo sta aspettando.
 */
suspend fun askUser(
    context: Context,
    title: String,
    message: String,
): UserAnswer {
    // La promessa: creata vuota, verra' riempita da un listener, cioe' da "fuori".
    val answer = CompletableDeferred<UserAnswer>()

    Log.d(TAG, "askUser(\"$title\"): mostro il dialog e mi sospendo in attesa")
    val dialog = AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Si") { _, _ -> answer.complete(UserAnswer.YES) }
        .setNegativeButton("No") { _, _ -> answer.complete(UserAnswer.NO) }
        // Senza questo, chiudere il dialog col tasto indietro lascerebbe la coroutine
        // sospesa PER SEMPRE: la promessa non verrebbe mai completata. E' l'errore
        // numero uno con questo pattern - ogni via d'uscita deve completare la promessa.
        .setOnCancelListener { answer.complete(UserAnswer.DISMISSED) }
        .create()

    dialog.show()

    return try {
        answer.await()   // <-- qui la coroutine si ferma, senza bloccare il main thread
    } finally {
        // Se la coroutine viene cancellata (es. l'utente lascia lo schermo mentre il dialog
        // e' aperto) dobbiamo comunque togliere di mezzo la finestra, altrimenti resta
        // appesa a un Fragment ormai morto -> "leaked window" / crash.
        dialog.dismiss()
        Log.d(TAG, "askUser(\"$title\"): dialog chiuso")
    }
}

/**
 * DEMO 3 - I DUE CONCETTI INSIEME.
 *
 * Se due flussi chiedono qualcosa all'utente nello stesso momento, i due dialog si
 * sovrappongono: l'utente ne vede uno solo e l'altro resta sotto, oppure risponde a una
 * domanda credendo di rispondere all'altra.
 *
 * Il Mutex mette in fila le richieste: il secondo flusso si sospende finche' il primo non
 * ha finito di parlare con l'utente. Nota che qui la sezione critica dura *quanto dura
 * l'attenzione dell'utente* - potenzialmente secondi. Con un lock bloccante sarebbe
 * impensabile; con un Mutex sospensivo l'app resta perfettamente reattiva.
 */
object DialogGate {

    private val mutex = Mutex()

    val isBusy: Boolean get() = mutex.isLocked

    suspend fun ask(context: Context, title: String, message: String): UserAnswer {
        if (mutex.isLocked) {
            Log.d(TAG, "DialogGate: c'e' gia' un dialog aperto, \"$title\" si mette in coda")
        }
        return mutex.withLock { askUser(context, title, message) }
    }
}
