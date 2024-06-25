/**
 * @file Pathing.java
 * @class Pathing
 * Pathing - Heavily borrowed and adapted from EthanApi.
 *
 * @author agge3
 * @version 1.0
 * @since 2024-06-15
 *
 * Special thanks to EthanApi and PiggyPlugins for API, inspiration, and a 
 * source of code at times.
 */

package com.aggeplugins.lib;

import com.aggeplugins.lib.*;
import com.aggeplugins.lib.export.*;

/* Begin shortest-path. */
//import net.runelite.api.Client;
//import net.runelite.api.KeyCode;
//import net.runelite.api.MenuAction;
//import net.runelite.api.MenuEntry;
//import net.runelite.api.Player;
//import net.runelite.api.Point;
//import net.runelite.api.SpriteID;
//import net.runelite.api.Varbits;
//import net.runelite.api.coords.WorldPoint;
//import net.runelite.api.events.GameTick;
//import net.runelite.api.events.MenuEntryAdded;
//import net.runelite.api.events.MenuOpened;
//import net.runelite.api.widgets.ComponentID;
//import net.runelite.api.widgets.Widget;
//import net.runelite.api.worldmap.WorldMap;
//import net.runelite.client.game.SpriteManager;
//import net.runelite.client.ui.JagexColors;
//import net.runelite.client.ui.overlay.OverlayManager;
//import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
//import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
//import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
//import net.runelite.client.util.ColorUtil;
//import net.runelite.client.util.ImageUtil;
//import net.runelite.client.util.Text;
//import shortestpath.pathfinder.CollisionMap;
//import shortestpath.pathfinder.Pathfinder;
//import shortestpath.pathfinder.PathfinderConfig;
//import shortestpath.pathfinder.SplitFlagMap;
//import shortestpath.*;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;
/* End shortest-path. */

import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.EthanApiPlugin.PathFinding.GlobalCollisionMap;
import com.example.Packets.MousePackets;
import com.example.Packets.MovementPackets;
import com.example.Packets.ObjectPackets;
import shortestpath.ShortestPathPlugin;
import static shortestpath.ShortestPathPlugin.getPathfinder;

import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.*;
import net.runelite.client.RuneLite;

import lombok.Getter;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Slf4j
public class Pathing {
    public enum Type {
        SHORTEST_PATH,
        ETHANS_API;
    }

    public Pathing(Context ctx)
    {
        this.ctx = ctx;
        log.info("Constructing Pathing!");
        init(); // on construction, start with an expected state
    }

    //public static WorldPoint getGoal()
    //{
    //    return goal; 
    //}

    public boolean calculatingPath()
    {
        if (type == Pathing.Type.SHORTEST_PATH) {
            if (calc.get()) {
                if (ShortestPathPlugin.getPathfinder() != null) {
                    if (ShortestPathPlugin.getPathfinder().isDone()) {
                        log.info("ShortestPath size: " +
                            ShortestPathPlugin.getPathfinder().getPath().size());
                        if (ShortestPathPlugin.getPathfinder().getPath().size() == 1) {
                            // re-calc
                            msg = new Message<>("GOAL", goal);
                            messageBus.send("PATHING", msg);
                        } else {
                            path = ShortestPathPlugin.getPathfinder().getPath();
                            calc.set(false);
                        }
                    }
                }
            }
        }
        return calc.get();
    }

    public void setType(Pathing.Type type)
    {
        switch(type) {
        case SHORTEST_PATH:
            this.type = Pathing.Type.SHORTEST_PATH;
            log.info("Pathing type set to: " + this.type);
            break;
        case ETHANS_API:
            this.type = Pathing.Type.ETHANS_API;
            log.info("Pathing type set to: " + this.type);
            break;
        default:
            // default is EthansApi, less semantics
            this.type = Pathing.Type.ETHANS_API;
            log.info("Pathing type set to: " + this.type);
            break;
        }
    }

    public boolean setPath()
    {
        if (goal != null) {
            switch(type) {
            case SHORTEST_PATH:
                calc.set(true);
                msg = new Message<>("GOAL", goal);
                messageBus.send("PATHING", msg);
                msg = null;
            break;
            case ETHANS_API:
                path = GlobalCollisionMap.findPath(goal);
            break;
            }
            return true;
        }
        return false;
    }

    /**
     * Set the pathing goal. If invalid, offset until a valid one is found.
     * @warning Recursive callback to find valid goal, can be expensive. Make
     * sure to set a valid goal to avoid.
     */
    public boolean setGoal(WorldPoint goal) {
        try {
            log.info("Path to: " + goal);
            this.reset(); // soft-reset, but keep mission-critical state
            this.goal = goal;
            log.info("Final pathing goal: " + this.goal);
            return true;
        } catch (NullPointerException e) {
            // Guard against an incorrectable recursive state. Confirm that the
            // goal is on the same plane as the current plane and block 
            // potential stack overfow. A goal is never going to be found if 
            // this condition is met anyway, we should have exited long ago.
            if (goal.getPlane() != getPos().getPlane() && goal.getX() > 1000) {
                // Recursive call with offset until a valid path exists.
                return setGoal(goal);
            } else {
                // Couldn't find a valid WorldPoint and never will.
                log.info("Could not correct to a valid WorldPoint!");
                return false;
            }
        }
    }

    /**
     * For testing inside a game loop. Always returns TRUE.
     *
     * @return TRUE, ALWAYS
     */
    public boolean test()
    {
        //log.info("Entering test!");
        //if (ShortestPathPlugin.getPathfinder() != null) {
        //    if (ShortestPathPlugin.getPathfinder().isDone()) {
        //        log.info("ShortestPath size: " +
        //            ShortestPathPlugin.getPathfinder().getPath().size());
        //        return false;
        //    }
        //}
        return true;
    }

    public boolean isPathingTo(WorldPoint goal)
    {
        log.info("Pathing to...");
        return goal != null && goal.equals(goal);
    }

    public boolean reachedGoal() 
    {
        return goal != null && 
               goal.equals(ctx.client.getLocalPlayer().getWorldLocation());
    }

    public boolean timeout(int n)
    {
        return true;
    }

    public boolean run() 
    {
        log.info("Pathing: Current goal is " + goal);
        log.info("Current path is " + path.get(0));
        ++ticks;

        if (reachedGoal() && goal == null) {
            log.info("Reached goal!");
            this.finalizer();
            return false;
        }

        if (path == null && ticks > 5) {
            log.info("Idling with no path. Exiting");
            this.finalizer();
            return false;
        }

        if (path != null && path.size() >= 1) {
            log.info("Current path goal is: " + path.get(path.size() - 1));
            ticks = 0;
            if (currPathDest != null && 
                !atCurrPathDest() && !EthanApiPlugin.isMoving()) {

                log.info("Stopped walking, clicking destination again");
                MousePackets.queueClickPacket();
                MovementPackets.queueMovement(currPathDest);
            }
                
            if (currPathDest == null || 
                atCurrPathDest() || !EthanApiPlugin.isMoving()) {

                log.info("Current path destination is " + currPathDest);

                int step = rand.nextInt((35 - 10) + 1) + 10;
                int max = step;
                for (int i = 0; i < step; i++) {
                    if (path.size() - 2 >= i) {
                        log.info("Current path is" + path.get(i));
                        if (isDoored(path.get(i), path.get(i + 1))) {
                            max = i;
                            break;
                        }
                    }
                }

                if (isDoored(getPos(), path.get(0))) {
                    log.info("Current path is " + currPathDest);
                    log.info("Door!");
                    WallObject wallObject = getTile(getPos()).getWallObject();
                    if (wallObject == null) {
                        wallObject = getTile(path.get(0)).getWallObject();
                    }
                    ObjectPackets.queueObjectAction(
                        wallObject, false, "Open", "Close");
                    return true;
                }

                step = Math.min(max, path.size() - 1);
                currPathDest = path.get(step);
                log.info("Current path destination is " + currPathDest);

                if (path.indexOf(currPathDest) == (path.size() - 1)) {
                    path = null;
                } else {
                    path = path.subList(step + 1, path.size());
                }

                if (currPathDest.equals(getPos())) {
                    return true;
                }
                
                log.info("Current path destination is " + currPathDest);
                log.info("Pathing: Taking a step");
                MousePackets.queueClickPacket();
                MovementPackets.queueMovement(currPathDest);
            }
        }
        return true;
    }

    private void init()
    {
        try {
            this.rand = new Random();
            this.calc = new AtomicBoolean(false);
            //this.path = new ArrayList<>(); // wait to init the path
            this.currPathDest = null;
            this.goal = null;
            this.ticks = 0;
        } catch (NullPointerException e) {
            log.info("Error: Unable to initialize pathing!");
        }
        // set default pathing type to EthansApi -- less semantics
        this.type = Pathing.Type.ETHANS_API;
        messageBus = messageBus.instance();
    }

    /**
     * Hard finalizer that ensures clean destruction.
     * @warning Can cause unexpected states!
     */
    private void finalizer()
    {
        // do a soft reset
        this.reset();
        // and hard destruct mission-critical, to ensure clean destruction
        this.rand = null;
        this.calc = null;
        this.type = null;
    }

    /**
     * Soft finalizer that resets instance while preserving mission-critical
     * state.
     */
    private void reset()
    {
        this.path = null;
        this.currPathDest = null;
        this.goal = null;
        this.ticks = 0;
    }

    private WorldPoint getPos()
    {
        return ctx.client.getLocalPlayer().getWorldLocation();
    }

    private boolean atCurrPathDest()
    {
        return currPathDest.equals(getPos());
    }
    
    private WorldPoint offset(WorldPoint wp)
    {
        int offset = 1;
        return new WorldPoint(wp.getX() - offset, wp.getY(), wp.getPlane());
    }

    private boolean isDoored(WorldPoint a, WorldPoint b)
    {
        Tile tA = getTile(a);
        Tile tB = getTile(b);
        if (tA == null || tB == null) {
            return false;
        }
        return isDoored(tA, tB);
    }

    private boolean isDoored(Tile a, Tile b)
    {
        WallObject wallObject = a.getWallObject();
        if (wallObject != null) {
            ObjectComposition objectComposition = EthanApiPlugin.getClient().getObjectDefinition(wallObject.getId());
            if (objectComposition == null) {
                return false;
            }
            boolean found = false;
            for (String action : objectComposition.getActions()) {
                if (action != null && action.equals("Open")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
            int orientation = wallObject.getOrientationA();
            if (orientation == 1) {
                //blocks west
                if (a.getWorldLocation().dx(-1).equals(b.getWorldLocation())) {
                    return true;
                }
            }
            if (orientation == 4) {
                //blocks east
                if (a.getWorldLocation().dx(+1).equals(b.getWorldLocation())) {
                    return true;
                }
            }
            if (orientation == 2) {
                //blocks north
                if (a.getWorldLocation().dy(1).equals(b.getWorldLocation())) {
                    return true;
                }
            }
            if (orientation == 8) {
                //blocks south
                return a.getWorldLocation().dy(-1).equals(b.getWorldLocation());
            }
        }
        WallObject wallObjectb = b.getWallObject();
        if (wallObjectb == null) {
            return false;
        }
        ObjectComposition objectCompositionb = EthanApiPlugin.getClient().getObjectDefinition(wallObjectb.getId());
        if (objectCompositionb == null) {
            return false;
        }
        boolean foundb = false;
        for (String action : objectCompositionb.getActions()) {
            if (action != null && action.equals("Open")) {
                foundb = true;
                break;
            }
        }
        if (!foundb) {
            return false;
        }
        int orientationb = wallObjectb.getOrientationA();
        if (orientationb == 1) {
            //blocks east
            if (b.getWorldLocation().dx(-1).equals(a.getWorldLocation())) {
                return true;
            }
        }
        if (orientationb == 4) {
            //blocks south
            if (b.getWorldLocation().dx(+1).equals(a.getWorldLocation())) {
                return true;
            }
        }
        if (orientationb == 2) {
            //blocks south
            if (b.getWorldLocation().dy(+1).equals(a.getWorldLocation())) {
                return true;
            }
        }
        if (orientationb == 8) {
            //blocks north
            return b.getWorldLocation().dy(-1).equals(a.getWorldLocation());
        }
        return false;
    }

    private Tile getTile(WorldPoint point)
    {
        LocalPoint a = LocalPoint.fromWorld(EthanApiPlugin.getClient(), point);
        if (a == null) {
            return null;
        }
        return EthanApiPlugin.getClient().getScene().getTiles()[point.getPlane()][a.getSceneX()][a.getSceneY()];
    }

    // Pathing instance variables.
    private Random rand;
    private Context ctx;
    private List<WorldPoint> path;
    private WorldPoint currPathDest;
    private WorldPoint goal;
    private AtomicBoolean calc;
    private Pathing.Type type;
    private MessageBus messageBus;
    private Message<String, WorldPoint> msg;

    private int ticks;
}
