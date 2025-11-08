/**
 * @file BooleanWithdraw.java
 * @class BooleanWithdraw
 * Boolean withdraw items from bank state, with override to withdraw quantity
 * (or all).
 *
 * @author agge3
 * @version 1.0
 * @since 2024-07-08
 *
 */

package com.aggeplugins.lib.BooleanState;

import com.aggeplugins.lib.*;
import com.aggeplugins.lib.export.*;
import com.aggeplugins.lib.BooleanState.*;

import com.example.EthanApiPlugin.Collections.*;
import com.example.EthanApiPlugin.Collections.query.*;
import com.example.EthanApiPlugin.*;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.InteractionApi.*;
import com.piggyplugins.PiggyUtils.BreakHandler.ReflectBreakHandler;
import com.example.Packets.*;
import com.piggyplugins.PiggyUtils.API.*;

import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Iterator;

@Slf4j
public class BooleanWithdraw<T> extends BooleanState<T> {
    public BooleanWithdraw(Pair<List<Integer>, List<String>> items)
    {
        super((T) items);
        this.init();
    }

    private void init()
    {
        this.items = (Pair<List<Integer>, List<String>>) this.ctx;

        if (this.items.getLeft() == null)
            this.bankItems = Collections.<Integer>emptyList();
        else
            this.bankItems = items.getLeft();

        if (this.items.getRight() == null) {
            this.bankAmt = Collections.<String>emptyList();
        } else {
            this.bankAmt = items.getRight();
        }
    }

    private enum Type {
        ONE(false),
        WIDGET(true),
        MENU(true),
        NONE(true);

        Type(boolean noted)     { this.noted = noted; }
        boolean getNoted()      { return noted; }

        private final boolean noted;
    }

    private Type withdraw(String amt)
    {
        switch(amt) {
        case "1":
            return Type.ONE;
            break;
        case "5":
        case "10":
        case "All":
        case "All-but-1":
            return Type.MENU;
            break;
        default:
            return Type.WIDGET;
            break;
        }


    }

    /**
     * Deposit all items (except excluded) and withdraw items specified in
     * BooleanBankingState's constructor.
     *
     * @remark Pass null to BooleanBankingState's constructor to only deposit
     * items.
     */
    @Override
    public boolean run()
    {
        // State 1: Interacting with bank.
        if (!inBank()) {
            Action.interactBank();
            log.info("Waiting to finish interacting with bank...");
            return false; // wait to be in bank widget
        }

        // State 2: Deposit all items.
        if (!BankInventory.search().empty()) {
            BankUtil.depositAll();
        }

        // State 3: Withdraw desired items.
        Iterator<Integer> i = bankItems.iterator();
        Iterator<String> j = bankAmt.iterator();
        if (i.hasNext()) {
            Integer item = i.next();
            String action = "Withdraw-All";
            Type type = Type.NONE;

            if (!Inventory.search().withId(item).first().isPresent()) {
                if (j.hasNext()) {
                    String amt = j.next();
                    type = withdraw(amt);

                    if (type == Type.WIDGET) {
                        int intAmt = Integer.parseInt(amt);
                        Widget widget = bankWidget(item);
                        if (widget != null) {
							// xxx ethan's withdrawX(widget, int);
                            BankInteraction.withdrawX(widget, intAmt);
                        } else {
                            i.remove();
                        }
                    } else {
                        Widget widget = bankWidget(item);
                        if (widget != null) {
							// xxx ethan's useItem(widget, boolean, string)
                            BankInteraction.useItem(widget, action);
                        } else {
                            i.remove();
                        }
                    }
                }
            }

            // Guard bankAmt against unsafe index, because it could be null for
            // just Withdraw-All.
            if (!bankAmt.isEmpty())
                j.remove();

            // Safe to remove, it's in our inventory.
            i.remove();

            return false; // break-out and re-enter
        }

        // close the bank screen before exiting
        EthanApiPlugin.invoke(11, 786434, MenuAction.CC_OP.getId(), 1, -1, "", "", -1, -1);

        // both should be true
        return bankItems.isEmpty() && bankAmt.isEmpty();
    }

    private List<String> itemsToList(String items)
    {
        return LibUtil.stringToList(items);
    }

    private Widget bankWidget(int item)
    {
        Optional<Widget> widget = Bank.search().withId(item).first();
        if (widget.isPresent()) {
            return widget.get();
		}

        return null;
    }

    private boolean inventoryEmpty()
    {
        return Inventory.getEmptySlots() == 28;
    }

    //private boolean canNpcBank()
    //{
    //    AutomicBoolean found = new AtomicBoolean(false);
    //    NPCs.search()
    //        .withAction("Bank")
    //        .nearestToPlayer()
    //        .ifPresent(npc -> {
    //            found.set(true);
    //            NPCInteraction.interact(npc, "Bank");
    //    });
    //    return found.get();
    //}

    private boolean inBank()
    {
        // If the bank widget is not found, return false.
        return !Widgets.search().withId(786445).first().isEmpty();
    }

    private boolean pin()
    {
        if (Widgets.search().withId(13959169).first().isPresent()) {
            log.info("Unable to continue: Bank pin");
            return true;
        }
        return false;
    }

    private Pair<List<Integer>, List<String>> items;
    private List<Integer> bankItems;
    private List<String> bankAmt;

    private List<EquipmentItemWidget> equipment;
}
