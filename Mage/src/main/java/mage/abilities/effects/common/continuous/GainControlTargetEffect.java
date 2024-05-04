package mage.abilities.effects.common.continuous;

import mage.abilities.Ability;
import mage.abilities.Mode;
import mage.abilities.condition.Condition;
import mage.abilities.effects.ContinuousEffectImpl;
import mage.constants.Duration;
import mage.constants.Layer;
import mage.constants.Outcome;
import mage.constants.SubLayer;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;

import java.util.UUID;

/**
 * @author BetaSteward_at_googlemail.com
 */
public class GainControlTargetEffect extends ContinuousEffectImpl {

    protected UUID controllingPlayerId;
    private final boolean fixedControl;
    private boolean firstControlChange = true;
    private final Condition condition;

    public GainControlTargetEffect(Duration duration) {
        this(duration, false, null);
    }

    /**
     * @param duration
     * @param fixedControl Controlling player is fixed even if the controller of
     *                     the ability changes later
     */
    public GainControlTargetEffect(Duration duration, boolean fixedControl) {
        this(duration, fixedControl, null);
    }

    /**
     * @param duration
     * @param controllingPlayerId Player that controls the target creature
     */
    public GainControlTargetEffect(Duration duration, UUID controllingPlayerId) {
        this(duration, true, controllingPlayerId);

    }

    public GainControlTargetEffect(Duration duration, boolean fixedControl, UUID controllingPlayerId) {
        this(duration, fixedControl, controllingPlayerId, null);
    }

    public GainControlTargetEffect(Duration duration, boolean fixedControl, UUID controllingPlayerId, Condition condition) {
        super(duration, Layer.ControlChangingEffects_2, SubLayer.NA, Outcome.GainControl);
        this.controllingPlayerId = controllingPlayerId;
        this.fixedControl = fixedControl;
        this.condition = condition;
    }

    protected GainControlTargetEffect(final GainControlTargetEffect effect) {
        super(effect);
        this.controllingPlayerId = effect.controllingPlayerId;
        this.fixedControl = effect.fixedControl;
        this.condition = effect.condition;
        this.firstControlChange = effect.firstControlChange;
    }

    @Override
    public GainControlTargetEffect copy() {
        return new GainControlTargetEffect(this);
    }

    @Override
    public void init(Ability source, Game game) {
        if (this.controllingPlayerId == null && fixedControl) {
            this.controllingPlayerId = source.getControllerId();
        }
        super.init(source, game);
    }

    @Override
    public boolean apply(Game game, Ability source) {
        Player controller = game.getPlayer(source.getControllerId());
        if (controller == null) {
            discard(); // controller no longer exists
            return false;
        }
        boolean oneTargetStillExists = false;
        for (UUID permanentId : getTargetPointer().getTargets(game, source)) {
            Permanent permanent = game.getPermanent(permanentId);
            if (permanent == null) {
                continue;
            }
            oneTargetStillExists = true;
            if (permanent.isControlledBy(controllingPlayerId)) {
                continue;
            }
            boolean controlChanged = false;
            if (controllingPlayerId != null) {
                if (permanent.changeControllerId(controllingPlayerId, game, source)) {
                    controlChanged = true;
                }
            } else {
                if (permanent.changeControllerId(source.getControllerId(), game, source)) {
                    controlChanged = true;
                }
            }
            if (source.isActivatedAbility()
                    && firstControlChange && !controlChanged) {
                // If it was not possible to get control of target permanent by the activated ability the first time it took place
                // the effect failed (e.g. because of Guardian Beast) and must be discarded
                // This does not handle correctly multiple targets at once
                discard();
            }
            if (condition != null && !condition.apply(game, source)) {
                discard();
            }
        }
        // no valid target exists and the controller is no longer in the game, effect can be discarded
        if (!oneTargetStillExists || !controller.isInGame()) {
            discard();
        }
        firstControlChange = false;
        return true;
    }

    @Override
    public String getText(Mode mode) {
        if (staticText != null && !staticText.isEmpty()) {
            return staticText;
        }
        return "gain control of " + getTargetPointer().describeTargets(mode.getTargets(), "that creature")
                + (duration.toString().isEmpty() || duration == Duration.EndOfGame ? "" : " " + duration.toString());
    }
}
