package com.example.adbreceiver.lab

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DEMO 1 - MUTEX.
 *
 * Un contatore condiviso da piu' "lavoratori" che incrementano in parallelo.
 *
 * Il trucco didattico e' che l'incremento NON e' una singola istruzione: e'
 * LEGGI -> (pausa) -> SCRIVI. La pausa e' una delay(), cioe' un punto di sospensione:
 * li' la coroutine molla il controllo e le altre entrano nella stessa funzione, leggendo
 * un valore ormai vecchio. Il risultato e' un "aggiornamento perso" (lost update).
 *
 * Da notare bene, perche' e' il punto che confonde quasi tutti: questo codice gira TUTTO
 * sul main thread. Non c'e' nessun parallelismo vero, nessuna race sulla memoria, eppure
 * il conto sbaglia lo stesso. Il Mutex non serve a proteggere il *thread*: serve a rendere
 * indivisibile una *sequenza di passi* che contiene sospensioni.
 */
class SharedCounter {

    private val mutex = Mutex()

    var value: Int = 0
        private set

    /** Quante coroutine sono attualmente dentro la sezione critica (per la UI). */
    var insideCriticalSection: Int = 0
        private set

    /**
     * VERSIONE ROTTA: nessuna protezione.
     * Lanciata da N lavoratori insieme, il totale finale sara' molto piu' basso di N.
     */
    suspend fun incrementUnsafe(stepDelayMs: Long) {
        insideCriticalSection++
        val current = value          // 1. LEGGO
        delay(stepDelayMs)           // 2. mi sospendo -> gli altri leggono lo stesso valore
        value = current + 1          // 3. SCRIVO, sovrascrivendo il lavoro altrui
        insideCriticalSection--
    }

    /**
     * VERSIONE CORRETTA: la sequenza leggi-scrivi e' una sezione critica.
     *
     * withLock { } equivale a lock() + try/finally + unlock(), ma rilascia il lock anche in
     * caso di eccezione o di cancellazione della coroutine. Usa sempre questa forma.
     *
     * Mentre uno e' dentro, gli altri si SOSPENDONO (non bloccano il thread!). Questa e' la
     * differenza con synchronized: sul main thread di Android un lock bloccante congelerebbe
     * l'interfaccia, un Mutex no - infatti durante la demo la UI resta fluida.
     */
    suspend fun incrementSafe(stepDelayMs: Long) {
        mutex.withLock {
            insideCriticalSection++  // con il lock, questo contatore non superera' mai 1
            val current = value
            delay(stepDelayMs)
            value = current + 1
            insideCriticalSection--
        }
    }

    fun reset() {
        value = 0
        insideCriticalSection = 0
    }
}
