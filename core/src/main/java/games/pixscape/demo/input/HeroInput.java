package games.pixscape.demo.input;

/** Movement and attack commands consumed by the hero gameplay system. */
public interface HeroInput {
    float moveX();

    float moveY();

    boolean attackJustPressed();
}
