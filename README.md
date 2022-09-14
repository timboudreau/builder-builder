Builder Builder
===============

A Java annotation and annotation processor for creating builders.

In it's simplest form, usage is simply:

1. Create a class that takes some constructor arguments.
2. Annotate it with `GenerateBuilder`.

That's it.  A class will be generated called with the same name as your class with `Builder` appended, in the same package.

Works with types that take generics and similar.

Generation Styles
=================

Two builder styles are available:

  * The default mode generates a set of builder classes such that it is impossible *at compile time* to create an
    incompletely initialized object, because you only are returned a builder type that **has** a build method when
    all but one of the required parameters have been supplied (there will be a `buildWith$NAME()` method that takes
    the final parameter.
      * This may result in a large number of interim builder classes, but in practice, users of builders will use a
        fluent API and be unaware of them
      * There is a tremendous advantage to turning runtime errors into compile-time errors - use this mode where practical
      * If the type takes an enormous number of parameters and the resulting source file might exceed `javac`'s file
        length limit, it will fail over to `FLAT` mode
  * FLAT - This generates a more typical builder class, with a build method visible at all times, which simply throws
    an IllegalStateException at runtime if a parameter was not provided.

Constraints
===========

A number of annotations can be applied to constructor *parameters* to constrain the legal values, and
those constraints will be enforced by the builder.

### Optional and Defaulted values

  * The `@Optionally` annotation allows you to mark a parameter as nullable.  If you set `defaulted=true`, then for
    types have a logical "null value" (zero, the empty string, an empty array or list) you will get that as a default
    * Default values for booleans, numbers and strings can be provided here

### Numeric Constraints

Annotate parameters with `IntMin / IntMax / FloatMin / FloatMax / LongMin / LongMax / DoubleMin / DoubleMax / ByteMin / ByteMax`
to set minimum and/or maximum allowable values.

### String Constraints

Annotatate string parameters with `StringPattern` to enforce a regular expression on the string, and optional minimum and
maximum length values.

### Collection Constraints

`@CollectionConstraint` applies to `java.util.Collection` types, `java.util.Map` types, as well as arrays, and lets you
set minimum and maximum sizes, forbid null values, and force a type check of each element.

