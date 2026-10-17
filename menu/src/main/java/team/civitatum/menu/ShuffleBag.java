package team.civitatum.menu;

import java.util.Random;

/** Plays every track once per round in random order; a new round never starts with the track that just ended. */
public final class ShuffleBag {
    private final Random random;
    private final int[] order;
    private int cursor;
    private int last = -1;

    public ShuffleBag(int size, Random random) {
        if (size < 1) throw new IllegalArgumentException("Empty playlist");
        this.random = random;
        this.order = new int[size];
        this.cursor = size;
    }

    public int next() {
        if (cursor == order.length) {
            for (int i = 0; i < order.length; i++) order[i] = i;
            for (int i = order.length - 1; i > 0; i--) swap(i, random.nextInt(i + 1));
            if (order.length > 1 && order[0] == last) swap(0, 1 + random.nextInt(order.length - 1));
            cursor = 0;
        }
        return last = order[cursor++];
    }

    private void swap(int a, int b) {
        int tmp = order[a];
        order[a] = order[b];
        order[b] = tmp;
    }
}
