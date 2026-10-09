package org.freevia.sudokubuddy.app

/** Coalesces pending automatic reports to the newest revision of each history entry. */
internal class AutoSubmissionQueue<T> {
    data class Item<T>(
        val entryId: Long,
        val photo: Any,
        val revision: Int,
        val payload: T,
    )

    private val pending = LinkedHashMap<Long, Item<T>>()

    fun offer(entryId: Long, photo: Any, revision: Int, payload: T) {
        val previous = pending[entryId]
        if (previous == null || revision >= previous.revision) {
            pending[entryId] = Item(entryId, photo, revision, payload)
        }
    }

    fun poll(): Item<T>? {
        val first = pending.entries.firstOrNull() ?: return null
        pending.remove(first.key)
        return first.value
    }

    fun markSubmitted(entryId: Long, revision: Int) {
        val item = pending[entryId] ?: return
        if (item.revision <= revision) pending.remove(entryId)
    }

    /** Do not immediately retry the same failed archive; a newer revision remains queued. */
    fun discardThrough(entryId: Long, revision: Int) {
        val item = pending[entryId] ?: return
        if (item.revision <= revision) pending.remove(entryId)
    }

    fun clear() = pending.clear()
    val isEmpty: Boolean get() = pending.isEmpty()
}
