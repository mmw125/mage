package mage.abilities.effects.common;

import mage.MageObject;
import mage.MageObjectReference;
import mage.abilities.Ability;
import mage.abilities.effects.ContinuousEffectImpl;
import mage.cards.Card;
import mage.constants.*;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.game.permanent.PermanentCard;
import mage.game.permanent.PermanentToken;
import mage.util.CardUtil;
import mage.util.functions.CopyApplier;

import java.util.UUID;

/**
 * Make battlefield's permanent as a copy of the source object
 * (source can be a card or another permanent)
 *
 * @author BetaSteward_at_googlemail.com
 */
public class CopyEffect extends ContinuousEffectImpl {

    protected MageObject copyFromObject;
    protected UUID copyToObjectId;
    protected CopyApplier applier;

    public CopyEffect(MageObject copyFromObject, UUID copyToObjectId) {
        this(Duration.Custom, copyFromObject, copyToObjectId);
    }

    public CopyEffect(Duration duration, MageObject copyFromObject, UUID copyToObjectId) {
        super(duration, Layer.CopyEffects_1, SubLayer.CopyEffects_1a, Outcome.BecomeCreature);
        this.copyFromObject = copyFromObject;
        this.copyToObjectId = copyToObjectId;
    }

    protected CopyEffect(final CopyEffect effect) {
        super(effect);
        this.copyFromObject = effect.copyFromObject.copy();
        this.copyToObjectId = effect.copyToObjectId;
        this.applier = effect.applier;
    }

    @Override
    public void init(Ability source, Game game) {
        super.init(source, game);

        // must copy the default side of the card (example: clone with mdf card)
        if (!(copyFromObject instanceof Permanent) && (copyFromObject instanceof Card)) {
            Card newBluePrint = CardUtil.getDefaultCardSideForBattlefield(game, (Card) copyFromObject);
            this.copyFromObject = new PermanentCard(newBluePrint, source.getControllerId(), game);
        }

        Permanent permanent = game.getPermanent(copyToObjectId);
        if (permanent != null) {
            affectedObjectList.add(new MageObjectReference(permanent, game));
        } else if (source.getAbilityType() == AbilityType.STATIC) {
            // for replacement effects that let a permanent enter the battlefield as a copy of another permanent we need to apply that copy
            // before the permanent is added to the battlefield
            permanent = game.getPermanentEntering(copyToObjectId);
            if (permanent != null) {
                copyToPermanent(permanent, game, source);
                // set reference to the permanent later on the battlefield so we have to add already one (if no token) to the zone change counter
                int ZCCDiff = 1;
                if (permanent instanceof PermanentToken) {
                    ZCCDiff = 0;
                }
                affectedObjectList.add(new MageObjectReference(permanent.getId(), game.getState().getZoneChangeCounter(copyToObjectId) + ZCCDiff, game));
            }
        }
    }

    @Override
    public boolean apply(Game game, Ability source) {
        if (affectedObjectList.isEmpty()) {
            this.discard();
            return false;
        }
        Permanent permanent = affectedObjectList.get(0).getPermanent(game);
        if (permanent == null) {
            if (!game.getShortLivingLKI(getSourceId(), Zone.BATTLEFIELD)) {
                discard();
                return false;
            }
            // As long as the permanent is still in the short living LKI continue to copy to get triggered abilities to TriggeredAbilities for dies events.
            permanent = (Permanent) game.getLastKnownInformation(getSourceId(), Zone.BATTLEFIELD, source.getSourceObjectZoneChangeCounter());
            if (permanent == null) {
                discard();
                return false;
            }
        }
        return copyToPermanent(permanent, game, source);
    }

    protected boolean copyToPermanent(Permanent permanent, Game game, Ability source) {
        if (copyFromObject.getCopyFrom() != null) {
            // copy from temp blueprints (they are already copies)
            permanent.setCopy(true, copyFromObject.getCopyFrom());
        } else {
            // copy object to object
            permanent.setCopy(true, copyFromObject);
        }
        permanent.setName(copyFromObject.getName());
        permanent.getColor(game).setColor(copyFromObject.getColor(game));
        permanent.getManaCost().clear();
        permanent.getManaCost().add(copyFromObject.getManaCost().copy());
        permanent.removeAllCardTypes(game);
        for (CardType type : copyFromObject.getCardType(game)) {
            permanent.addCardType(game, type);
        }

        permanent.removeAllSubTypes(game);
        permanent.copySubTypesFrom(game, copyFromObject);

        permanent.removeAllSuperTypes(game);
        for (SuperType type : copyFromObject.getSuperType(game)) {
            permanent.addSuperType(game, type);
        }

        permanent.removeAllAbilities(source.getSourceId(), game);
        if (copyFromObject instanceof Permanent) {
            for (Ability ability : ((Permanent) copyFromObject).getAbilities(game)) {
                permanent.addAbility(ability, getSourceId(), game, true);
            }
        } else {
            for (Ability ability : copyFromObject.getAbilities()) {
                permanent.addAbility(ability, getSourceId(), game, true);
            }
        }

        // Primal Clay example:
        // If a creature that’s already on the battlefield becomes a copy of this creature, it copies the power, toughness,
        // and abilities that were chosen for this creature as it entered the battlefield. (2018-03-16)
        permanent.getPower().setModifiedBaseValue(copyFromObject.getPower().getModifiedBaseValue());
        permanent.getToughness().setModifiedBaseValue(copyFromObject.getToughness().getModifiedBaseValue());
        permanent.setStartingLoyalty(copyFromObject.getStartingLoyalty());
        permanent.setStartingDefense(copyFromObject.getStartingDefense());
        if (copyFromObject instanceof Permanent) {
            Permanent targetPermanent = (Permanent) copyFromObject;
            //707.2. When copying an object, the copy acquires the copiable values of the original object’s characteristics [..]
            //110.5. A permanent's status is its physical state. There are four status categories, each of which has two possible values:
            // tapped/untapped, flipped/unflipped, face up/face down, and phased in/phased out.
            // Each permanent always has one of these values for each of these categories.
            //110.5a Status is not a characteristic, though it may affect a permanent’s characteristics.
            //Being transformed is not a copiable characteristic, nor is the back side of a DFC
            //permanent.setTransformed(targetPermanent.isTransformed());
            //permanent.setSecondCardFace(targetPermanent.getSecondCardFace());
            permanent.setFlipCard(targetPermanent.isFlipCard());
            permanent.setFlipCardName(targetPermanent.getFlipCardName());
            permanent.setPrototyped(targetPermanent.isPrototyped());
        }

        CardUtil.copySetAndCardNumber(permanent, copyFromObject);

        return true;
    }

    @Override
    public CopyEffect copy() {
        return new CopyEffect(this);
    }

    public MageObject getTarget() {
        return copyFromObject;
    }

    public void setTarget(MageObject target) {
        this.copyFromObject = target;
    }

    public UUID getSourceId() {
        return copyToObjectId;
    }

    public CopyApplier getApplier() {
        return applier;
    }

    public CopyEffect setApplier(CopyApplier applier) {
        this.applier = applier;
        return this;
    }

}
