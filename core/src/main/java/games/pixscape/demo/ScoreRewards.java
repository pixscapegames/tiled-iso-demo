package games.pixscape.demo;

import java.util.ArrayList;

/** Records earned points immediately and displays each reward at most once. */
final class ScoreRewards {
    static final class Ticket {
        private final int points;
        private final int session;
        private boolean delivered;

        private Ticket(int points, int session) {
            this.points = points;
            this.session = session;
        }
    }

    private final ArrayList<Ticket> pending = new ArrayList<>();
    private int session;
    private int earned;
    private int displayed;

    Ticket earn(int points) {
        if (points <= 0) throw new IllegalArgumentException("Reward points must be positive.");
        earned += points;
        Ticket ticket = new Ticket(points, session);
        pending.add(ticket);
        return ticket;
    }

    boolean display(Ticket ticket) {
        if (ticket == null || ticket.session != session || ticket.delivered) return false;
        ticket.delivered = true;
        pending.remove(ticket);
        displayed += ticket.points;
        return true;
    }

    boolean flush() {
        if (pending.isEmpty()) return false;
        for (int i = 0; i < pending.size(); i++) {
            Ticket ticket = pending.get(i);
            ticket.delivered = true;
            displayed += ticket.points;
        }
        pending.clear();
        return true;
    }

    void newSession() {
        flush();
        session++;
        earned = 0;
        displayed = 0;
    }

    int earned() { return earned; }

    int displayed() { return displayed; }
}
