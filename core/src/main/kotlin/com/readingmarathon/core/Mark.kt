package com.readingmarathon.core

/**
 * A meaning, not a picture: the app draws each one as a vector icon. Keeps emoji out of the data
 * and lets the UI choose a consistent icon set.
 */
enum class Mark {
    POINTS, HOURS, BOOKS, QUOTES, DAYS, FAST, WEEK, MONTH,
    STREAK, EARLY_FINISH, DISCUSSION,
    UPCOMING, RACE, FIRST, GROUP, JOINED, PROGRESS, LEADER, INFO,
}
