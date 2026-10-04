package dev.mellow.core.model

/** The orders the library's album, artist and track lists can be shown in. */
enum class LibrarySort {
    /** Newest additions first. Artists keep their sort-name order. */
    RecentlyAdded,

    /** By name, A to Z. */
    NameAscending,

    /** By name, Z to A. */
    NameDescending,

    /** Albums by release year, newest first; tracks by album name, Z to A. Artists keep their sort-name order. */
    Year,
}
