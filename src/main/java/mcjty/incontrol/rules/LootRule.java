package mcjty.incontrol.rules;

import com.google.gson.JsonElement;
import mcjty.incontrol.InControl;
import mcjty.incontrol.rules.support.GenericRuleEvaluator;
import mcjty.incontrol.rules.support.IEventQuery;
import mcjty.incontrol.typed.Attribute;
import mcjty.incontrol.typed.AttributeMap;
import mcjty.incontrol.typed.GenericAttributeMapFactory;
import mcjty.incontrol.varia.Tools;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static mcjty.incontrol.rules.support.RuleKeys.*;

public class LootRule {

    private static final GenericAttributeMapFactory FACTORY = new GenericAttributeMapFactory();
    public static final IEventQuery<LivingDropsEvent> EVENT_QUERY = new IEventQuery<LivingDropsEvent>() {
        @Override
        public World getWorld(LivingDropsEvent o) {
            return o.getEntity().getEntityWorld();
        }

        @Override
        public BlockPos getPos(LivingDropsEvent o) {
            LivingDropsEvent s = o;
            return s.getEntity().getPosition();
        }

        @Override
        public int getY(LivingDropsEvent o) {
            return o.getEntity().getPosition().getY();
        }

        @Override
        public Entity getEntity(LivingDropsEvent o) {
            return o.getEntity();
        }

        @Override
        public DamageSource getSource(LivingDropsEvent o) {
            return o.getSource();
        }

        @Override
        public Entity getAttacker(LivingDropsEvent o) {
            return o.getSource().getTrueSource();
        }
    };

    static {
        FACTORY
                .attribute(Attribute.create(MINTIME))
                .attribute(Attribute.create(MAXTIME))
                .attribute(Attribute.create(MINLIGHT))
                .attribute(Attribute.create(MAXLIGHT))
                .attribute(Attribute.create(MINHEIGHT))
                .attribute(Attribute.create(MAXHEIGHT))
                .attribute(Attribute.create(MINDIFFICULTY))
                .attribute(Attribute.create(MAXDIFFICULTY))
                .attribute(Attribute.create(MINSPAWNDIST))
                .attribute(Attribute.create(MAXSPAWNDIST))
                .attribute(Attribute.create(RANDOM))
                .attribute(Attribute.create(INBUILDING))
                .attribute(Attribute.create(INCITY))
                .attribute(Attribute.create(GAMESTAGE))
                .attribute(Attribute.create(INSTREET))
                .attribute(Attribute.create(INSPHERE))
                .attribute(Attribute.create(PASSIVE))
                .attribute(Attribute.create(HOSTILE))
                .attribute(Attribute.create(SEESKY))
                .attribute(Attribute.create(WEATHER))
                .attribute(Attribute.create(TEMPCATEGORY))
                .attribute(Attribute.create(DIFFICULTY))
                .attribute(Attribute.create(STRUCTURE))
                .attribute(Attribute.create(PLAYER))
                .attribute(Attribute.create(REALPLAYER))
                .attribute(Attribute.create(FAKEPLAYER))
                .attribute(Attribute.create(PROJECTILE))
                .attribute(Attribute.create(EXPLOSION))
                .attribute(Attribute.create(FIRE))
                .attribute(Attribute.create(MAGIC))
                .attribute(Attribute.createMulti(MOB))
                .attribute(Attribute.createMulti(MOD))
                .attribute(Attribute.createMulti(BLOCK))
                .attribute(Attribute.createMulti(BIOME))
                .attribute(Attribute.createMulti(BIOMETYPE))
                .attribute(Attribute.createMulti(DIMENSION))
                .attribute(Attribute.createMulti(SOURCE))
                .attribute(Attribute.createMulti(HELDITEM))

                .attribute(Attribute.create(ACTION_ITEMNBT))
                .attribute(Attribute.create(ACTION_ITEMCOUNT))
                .attribute(Attribute.createMulti(ACTION_ITEM))
                .attribute(Attribute.createMulti(ACTION_REMOVE))
                .attribute(Attribute.create(ACTION_REMOVEALL))
        ;
    }

    private final GenericRuleEvaluator ruleEvaluator;
    private List<Pair<ItemStack, Function<Integer, Integer>>> toRemoveItems = new ArrayList<>();
    private List<Pair<ItemStack, Function<Integer, Integer>>> toAddItems = new ArrayList<>();
    private boolean removeAll = false;

    private LootRule(AttributeMap map) {
        ruleEvaluator = new GenericRuleEvaluator(map);
        addActions(map);
    }

    private void addActions(AttributeMap map) {
        if (map.has(ACTION_ITEM)) {
            addItem(map);
        }
        if (map.has(ACTION_REMOVE)) {
            removeItem(map);
        }
        if (map.has(ACTION_REMOVEALL)) {
            removeAll = map.get(ACTION_REMOVEALL);
        }
    }

    public List<Pair<ItemStack, Function<Integer, Integer>>> getToRemoveItems() {
        return toRemoveItems;
    }

    public boolean isRemoveAll() {
        return removeAll;
    }

    public List<Pair<ItemStack, Function<Integer, Integer>>> getToAddItems() {
        return toAddItems;
    }

    private Function<Integer, Integer> getCountFunction(@Nullable String itemcount) {
        if (itemcount == null) {
            return looting -> 1;
        }
        String[] loottable = StringUtils.split(itemcount, '/');
        int[] min = new int[loottable.length];
        int[] max = new int[loottable.length];
        for (int i = 0 ; i < loottable.length ; i++) {
            String[] minmax = StringUtils.split(loottable[i], '-');
            if (minmax.length == 1) {
                try {
                    min[i] = max[i] = Integer.parseInt(minmax[0]);
                } catch (NumberFormatException e) {
                    InControl.logger.log(Level.ERROR, "Bad amount specified in loot rule: " + minmax);
                    min[i] = max[i] = 1;
                }
            } else if (minmax.length == 2) {
                try {
                    min[i] = Integer.parseInt(minmax[0]);
                    max[i] = Integer.parseInt(minmax[1]);
                } catch (NumberFormatException e) {
                    InControl.logger.log(Level.ERROR, "Bad amounts specified in loot rule: " + minmax);
                    min[i] = max[i] = 1;
                }
            } else {
                InControl.logger.log(Level.ERROR, "Bad amount range specified in loot rule: " + minmax);
                min[i] = max[i] = 1;
            }
        }

        if (loottable.length == 1) {
            // Easy case
            if (min[0] == max[0]) {
                return looting -> min[0];
            } else {
                return looting -> rnd.nextInt(max[0]-min[0]+1) + min[0];
            }
        } else {
            return looting -> {
                if (looting >= min.length) {
                    return rnd.nextInt(max[min.length-1] - min[min.length-1] + 1) + min[min.length-1];
                } else if (looting >= 0) {
                    return rnd.nextInt(max[looting] - min[looting] + 1) + min[looting];
                } else {
                    return rnd.nextInt(max[0] - min[0] + 1) + min[0];
                }
            };
        }
    }

    private List<Pair<ItemStack, Function<Integer, Integer>>> getItems(List<String> itemNames, @Nullable String nbtJson,
                                     @Nullable String itemcount) {
        Function<Integer, Integer> countFunction = getCountFunction(itemcount);

        List<Pair<ItemStack, Function<Integer, Integer>>> items = new ArrayList<>();
        for (String name : itemNames) {
            ItemStack stack = Tools.parseStack(name);
            if (stack.isEmpty()) {
                InControl.logger.log(Level.ERROR, "Unknown item '" + name + "'!");
            } else {
                if (nbtJson != null) {
                    try {
                        stack.setTagCompound(JsonToNBT.getTagFromJson(nbtJson));
                    } catch (NBTException e) {
                        InControl.logger.log(Level.ERROR, "Bad nbt for '" + name + "'!");
                    }
                }
                items.add(Pair.of(stack, countFunction));
            }
        }
        return items;
    }

    private void addItem(AttributeMap map) {
        String nbt = map.get(ACTION_ITEMNBT);
        String itemcount = map.get(ACTION_ITEMCOUNT);
        toAddItems.addAll(getItems(map.getList(ACTION_ITEM), nbt, itemcount));
    }

    private void removeItem(AttributeMap map) {
        toRemoveItems.addAll(getItems(map.getList(ACTION_REMOVE), null, null));
    }

    private static Random rnd = new Random();

    public boolean match(LivingDropsEvent event) {
        return ruleEvaluator.match(event, EVENT_QUERY);
    }

    public static LootRule parse(JsonElement element) {
        if (element == null) {
            return null;
        } else {
            AttributeMap map = FACTORY.parse(element);
            return new LootRule(map);
        }
    }}
