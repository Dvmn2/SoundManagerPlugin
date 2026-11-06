package net.dvmn2.soundmanagerplugin.util;

import java.util.random.RandomGenerator;

/** Builds the order in which the tracks of one cycle of a playlist are played. Stateless. */
public final class PlayOrder {

    private PlayOrder() {
    }

    /** The indexes {@code 0..size-1} in their natural order. */
    public static int[] sequential(int size) {
        int[] order = new int[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        return order;
    }

    /**
     * The indexes {@code 0..size-1} in random order (Fisher-Yates).
     *
     * @param avoidFirst index that must not come first, so that a track is not repeated at the junction of two
     *                   cycles; -1 for no restriction (ignored when {@code size} is 1)
     */
    public static int[] shuffled(int size, int avoidFirst, RandomGenerator random) {
        int[] order = sequential(size);
        for (int i = size - 1; i > 0; i--) {
            swap(order, i, random.nextInt(i + 1));
        }
        if (size > 1 && order[0] == avoidFirst) {
            swap(order, 0, 1 + random.nextInt(size - 1));
        }
        return order;
    }

    private static void swap(int[] array, int a, int b) {
        int tmp = array[a];
        array[a] = array[b];
        array[b] = tmp;
    }
}
