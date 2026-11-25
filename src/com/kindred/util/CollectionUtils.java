package com.kindred.util;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Small reusable GENERIC helper methods built on the Stream API.
 */
public final class CollectionUtils {

    private CollectionUtils() { }

    /**
     * Returns the n LARGEST items of any Comparable type.
     * Used for the admin "credit leaderboard" (User is Comparable by credits).
     *
     * <T extends Comparable<? super T>> is a BOUNDED type parameter: T must be
     * comparable to itself or to one of its parent types.
     */
    // SYLLABUS: Generics - generic method with bounded type parameter <T extends Comparable<? super T>>
    // SYLLABUS: Generics - lower bounded wildcard (? super T)
    // SYLLABUS: Stream sorted() + limit() + collect()
    public static <T extends Comparable<? super T>> List<T> topN(List<T> items, int n) {
        return items.stream()
                .sorted(Comparator.<T>reverseOrder())  // biggest first
                .limit(n)                              // keep only n
                .collect(Collectors.toList());
    }

    /**
     * Returns one PAGE of a list, e.g. page 2 with size 5 = items 6..10.
     * Used to show long histories page by page.
     */
    // SYLLABUS: Generics - generic method <T>
    // SYLLABUS: Stream skip() + limit()
    public static <T> List<T> page(List<T> items, int pageNumber, int pageSize) {
        return items.stream()
                .skip((long) (pageNumber - 1) * pageSize)  // jump over earlier pages
                .limit(pageSize)                           // take one page
                .collect(Collectors.toList());
    }

    /**
     * Adds up ANY list of numbers: List<Integer>, List<Double>, List<Long>...
     * List<? extends Number> means "a list of some subtype of Number" (read-only use).
     */
    // SYLLABUS: Generics - upper bounded wildcard (? extends Number)
    // SYLLABUS: Stream map (method reference Number::intValue)
    public static int sum(List<? extends Number> numbers) {
        return numbers.stream()
                .mapToInt(Number::intValue)
                .sum();
    }
}
