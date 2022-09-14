package com.timboudreau.bugdemo.attribution.bug.demo;

import com.timboudreau.bugdemo.attribution.bug.demo.BuilderBuilderDemoBuilder.BuilderBuilderDemoBuilderSansEmmm;
import com.timboudreau.bugdemo.attribution.bug.demo.BuilderBuilderDemoBuilder.BuilderBuilderDemoBuilderSansEmmmTTypeTheTeeThing;
import com.timboudreau.bugdemo.attribution.bug.demo.BuilderBuilderDemoBuilder.BuilderBuilderDemoBuilderSansEmmmTheRThing;
import com.timboudreau.bugdemo.attribution.bug.demo.BuilderBuilderDemoBuilder.BuilderBuilderDemoBuilderSansEmmmThing;
import com.timboudreau.bugdemo.attribution.bug.demo.BuilderBuilderDemoBuilder.BuilderBuilderDemoBuilderWithCountIntArrayName;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Test + demo code for &#064;GenerateBuilder
 *
 * @author Tim Boudreau
 */
public class BuilderBuilderDemoTest {

    // A consistent timestamp for comparisons
    private static final Instant WHEN = Instant.now();

    @Test
    public void testBuilder() throws IOException, ClassNotFoundException {
        // SIMPLE USAGE - and all you have to do is annotate a class with
        // @GenerateBuilder (and perhaps some parameter constraints or defaults)

        // Generates the code you wish you had, but would never write yourself:
        BuilderBuilderDemo<AtomicInteger, Instant, StringBuilder> result
                // AttributionBugDemo uses the default builder style - you cannot
                // possibly create an invalid object because you are literally never
                // returned an object with a build*() method until all the required
                // fields have been populated.  So it is impossible to make a mistake
                // with at runtime, because the compiler guarantees that it's impossible
                // to even TRY to create a half-initialized object.
                //
                // Constructor parameters annotated with @Optionally can be set on
                // any builder, and may have a default value
                = BuilderBuilderDemo.builder()
                        .withName("1234_1234_1234_1234_1234")
                        .withCount(23)
                        .withIntArray(6, 7, 8, 9, 10, 11) // constraint: values must be > 5
                        .withTType(AtomicInteger.class) // the return value acquires the type AtomicInteger here
                        .withTheTee(new AtomicInteger(5))
                        .<Instant>withTheR(WHEN) // and aquires another generic here
                        .withEmmm(Arrays.asList(
                                new StringBuilder("foo"),
                                new StringBuilder("bar"),
                                new StringBuilder("baz"))) // and here we acquire that the list type is StringBuilder
                        // build methods take the last remaining required
                        // parameter, and are named accordingly:
                        .buildWithThing(
                                // ThingBuilder uses the FLAT generation style,
                                // meaning you get a build method that throws if
                                // a parameter is missing
                                new ThingBuilder()
                                        .withShortValue((short) 30) // must be > 23
                                        .withStringValue("thing") // must be [a-z]+ 1-20 chars
                                        .build()
                        );

        // Also note that the constructor of AttributionBugDemo throws
        // IOException and ClassNotFoundException, therefore the build
        // methods also throw that.
        //
        // Since this is a fluent API, users will never see the interim
        // builder types, or need to know about them.
        //
        // Breaking it down, what we're getting under the hood is:
        BuilderBuilderDemoBuilderWithCountIntArrayName b1
                = BuilderBuilderDemo.builder()
                        .withName("1234_1234_1234_1234_1234")
                        .withCount(23)
                        .withIntArray(6, 7, 8, 9, 10, 11);

        // Acquiring AtomicInteger generic
        BuilderBuilderDemoBuilderSansEmmmTheRThing<AtomicInteger> b2
                = b1.withTType(AtomicInteger.class).withTheTee(new AtomicInteger(5));

        // And the Instant generic:
        BuilderBuilderDemoBuilderSansEmmmThing<AtomicInteger, Instant> b3
                = b2.<Instant>withTheR(WHEN);

        // Or we could do that in the opposite order:
        BuilderBuilderDemoBuilderSansEmmmTTypeTheTeeThing<Instant> b2a
                = b1.<Instant>withTheR(WHEN);

        // Notice that we arrive at the same type as b3 - while we are dealing in the
        // cartesian product of fields, since the set of fields is a SET, it is not the
        // explosion of builder types one might think
        BuilderBuilderDemoBuilderSansEmmmThing<AtomicInteger, Instant> b3a
                = b2a.withTType(AtomicInteger.class).withTheTee(new AtomicInteger(5));

        // If we set the Thing field here, we get a builder with a different "buildWith"
        // method
        BuilderBuilderDemoBuilderSansEmmm<AtomicInteger, Instant> b4
                = b3a.withThing(thingBuilder
                        -> thingBuilder
                        .withShortValue((short) 30)
                        .withStringValue("thing")
                        .build()
                );

        // In which case we have a build method that takes the one
        // remaining required property, the List<M> which must conform
        // to a complicated generic signature which StringBuilder matches:
        BuilderBuilderDemo<AtomicInteger, Instant, StringBuilder> result2
                = b4.buildWithEmmm(Arrays.asList(
                        new StringBuilder("foo"),
                        new StringBuilder("bar"),
                        new StringBuilder("baz")));

        // And just verify that both builders resulted in what we expected.
        // We can't do assertEquals(result1, result2) because AtomicInteger
        // does not implement it and hash code for value equality.
        assertEquals(new ThingBuilder()
                .withShortValue((short) 30)
                .withStringValue("thing")
                .build(), result.thing);
        assertEquals(new ThingBuilder()
                .withShortValue((short) 30)
                .withStringValue("thing")
                .build(), result2.thing);

        assertEquals(23, result.count);
        assertEquals(23, result2.count);
        assertEquals("1234_1234_1234_1234_1234", result.name);
        assertEquals("1234_1234_1234_1234_1234", result2.name);
        assertEquals(AtomicInteger.class, result.tType);
        assertEquals(AtomicInteger.class, result2.tType);
        assertEquals(5, result.theTee.get());
        assertEquals(5, result2.theTee.get());
        assertEquals(WHEN, result.theR);
        assertEquals(WHEN, result2.theR);
        assertArrayEquals(new int[]{6, 7, 8, 9, 10, 11}, result.intArray);
        assertArrayEquals(new int[]{6, 7, 8, 9, 10, 11}, result2.intArray);
        assertEquals(3, result.emmm.size());
        assertEquals(3, result2.emmm.size());
    }

}
