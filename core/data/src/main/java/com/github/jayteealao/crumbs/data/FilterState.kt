package com.github.jayteealao.crumbs.data

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

data class FilterState(
    val type: TypeFilter = TypeFilter.ALL,
    val selectedTags: ImmutableSet<String> = persistentSetOf(),
    val selectedCollectionTags: ImmutableSet<String> = persistentSetOf(),
) {
    val isEmpty: Boolean
        get() = type == TypeFilter.ALL &&
            selectedTags.isEmpty() &&
            selectedCollectionTags.isEmpty()
}
