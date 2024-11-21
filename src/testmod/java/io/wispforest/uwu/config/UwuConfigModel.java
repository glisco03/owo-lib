package io.wispforest.uwu.config;

import blue.endless.jankson.Comment;
import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.*;
import io.wispforest.owo.ui.core.Color;
import net.fabricmc.fabric.api.util.TriState;

import java.util.ArrayList;
import java.util.List;

@Sync(Option.SyncMode.OVERRIDE_CLIENT)
@Modmenu(modId = "uwu", uiModelId = "uwu:config")
@Config(name = "uwu", wrapperName = "UwuConfig")
public class UwuConfigModel {

    @SectionHeader("top")
    @RangeConstraint(min = 0, max = 56)
    public int aValue = 56;

    @RegexConstraint("[A-Za-z]{1,3}")
    public String regex = "yes";

    @Nest
    @Expanded
    @SectionHeader("nesting_yo?")
    public Nested nestingTime = new Nested();

    @PredicateConstraint("predicateFunction")
    public List<String> someOption = new ArrayList<>(List.of("1", "2", "3", "4", "5"));

    @Nest
    public RangeConstraintTest rangeTest = new RangeConstraintTest();

    public static class RangeConstraintTest {
        @RangeConstraint()
        public double completelyEmptyRangeConstraint = 69;

        @RangeConstraint(min = 0)
        public double minOnly = 69;

        @RangeConstraint(max = 100)
        public double maxOnly = 69;

        @RangeConstraint(min = 0, max = 100)
        public double minAndMax = 69;

        @RangeConstraint(min = 0, max = 100, allowSlider = false)
        public double noSlider = 69;

        @RangeConstraint(min = 0, max = 100, defaultOption = RangeConstraint.DefaultOptionType.TEXT_BOX)
        public double startWithTextBox = 69;
    }

    public String thisIsAStringValue = "\\bruh?";

    @SectionHeader("bottom")
    public List<String> thereAreStringsHere = new ArrayList<>(List.of("yes", "no"));

    @RestartRequired
    public WowValues broTheresAnEnum = WowValues.FIRST;

    public Color anEpicColor = Color.BLUE;

    @WithAlpha
    public Color anEpicColorWithAlpha = Color.GREEN;

    @ExcludeFromScreen
    public String noSeeingThis = "yep, never";

    public static class Nested {
        public boolean togglee = false;
        public boolean yesThisIsAlsoNested = true;

        @Nest
        @Comment("Commented nesting")
        public SuperNested nestingTimeIntensifies = new SuperNested();

        @Sync(Option.SyncMode.INFORM_SERVER)
        public List<Integer> nestedIntegers = new ArrayList<>(List.of(69, 34, 35, 420));
    }

    public static class SuperNested {
        public byte wowSoNested;
    }

    public enum WowValues {
        FIRST, SECOND, THIRD, FOURTH;
    }

    // so we declare a predicate method
    public static boolean predicateFunction(List<String> list) {
        // and do the check in here
        // this could be arbitrarily complex code, but
        // we'll keep it simple for this demonstration
        return list.size() == 5;
    }
}
