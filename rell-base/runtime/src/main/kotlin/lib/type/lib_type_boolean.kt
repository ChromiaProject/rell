/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type

object Lib_Type_Boolean {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("boolean", since = "0.6.0") {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN))
            comment("""
                A data type with two possible values: `true` and `false`.

                Boolean logic can be performed with the `and`, `or` and `not` operators, for example:

                ```rell
                >>> true and false
                false
                >>> true or false
                true
                >>> not true
                false
                >>> not false
                true
                ```

                In addition, booleans are used in ternary expressions:

                ```rell
                >>> if (true) 1 else 2
                1
                >>> if (false) 1 else 2
                2
                ```

                The `in` operator returns a boolean:

                ```rell
                >>> 1 in list<integer>()
                false
                >>> 1 in [1, 2]
                true
                ```

                Booleans are used in `if`- and `if/else`-statements:

                ```rell
                >>> if (false) { print("hello"); } else { print("goodbye"); }
                goodbye
                >>> if (false) { print("hello"); }
                >>> if (true) { print("hello"); }
                hello
                >>>
                ```

                Boolean expressions are the basis of zero-argument `when`-statements (each expression to the left of a
                '`->`' symbol has boolean type (and in this context `else` is equivalent to `true`)):

                ```rell
                when {
                    x == 1 -> return 'One';
                    x >= 2 and x <= 7 -> return 'Several';
                    x == 11, x == 111 -> return 'Magic number';
                    some_value > 1000 -> return 'Special case';
                    else -> return 'Unknown';
                }
                ```

                Boolean conditions are used in while loops:

                ```rell
                while (x < 10) {
                    print(x);
                    x = x + 1;
                }
                ```

                Functions can have `boolean` return type (as can queries and operations), and indeed many functions and
                properties in the Rell standard library have `boolean` type.

                ```rell
                function foo(x: integer): boolean {
                    return x >= 10;
                }
                ```
            """)
        }
    }
}

