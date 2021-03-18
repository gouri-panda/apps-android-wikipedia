package org.wikipedia.events

import org.wikipedia.readinglist.database.ReadingListPage

class ArticleSavedOrDeletedEvent(isAdded: Boolean, vararg pages: ReadingListPage) {
    val pages: Array<ReadingListPage>
    val isAdded: Boolean

    init {
        this.pages = pages
        this.isAdded = isAdded
    }
}