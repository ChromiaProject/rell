/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import kotlin.test.Test

internal class MFunctionWildcardTest: BaseMFunctionTest() {
    @Test fun testGlobalInput() {
        chkGlobalEx("<T>(data<T>,T):unit", "data<int>,int", "[T=int] (a:data<int>,b:int):unit")
        chkGlobalEx("<T>(data<T>,T):unit", "data<-int>,int", "n/a")
        chkGlobalEx("<T>(data<T>,T):unit", "data<+int>,int", "[T=CAP<+int>] (a:data<CAP<+int>>,b:CAP<+int>):unit")
    }

    @Test fun testGlobalOutput() {
        chkGlobalEx("<T>(data<T>):T", "data<int>", "[T=int] (a:data<int>):int")
        chkGlobalEx("<T>(data<T>):T", "data<-int>", "[T=CAP<-int>] (a:data<CAP<-int>>):int")
        chkGlobalEx("<T>(data<T>):T", "data<+int>", "[T=CAP<+int>] (a:data<CAP<+int>>):anything")
    }

    @Test fun testMemberInput() {
        chkMemberEx("data<int>", "(T):unit", "int", "(a:int):unit OK")
        chkMemberEx("data<-int>", "(T):unit", "int", "(a:CAP<-int>):unit n/a")
        chkMemberEx("data<+int>", "(T):unit", "int", "(a:CAP<+int>):unit OK")
    }

    @Test fun testMemberOutput() {
        chkMemberEx("data<int>", "():T", "", "():int OK")
        chkMemberEx("data<-int>", "():T", "", "():int OK")
        chkMemberEx("data<+int>", "():T", "", "():anything OK")
    }

    @Test fun testReturnFunction() {
        chkMemberEx("data<int>", "():()->T", "", "():()->int OK")
        chkMemberEx("data<-int>", "():()->T", "", "():()->int OK")
        chkMemberEx("data<+int>", "():()->T", "", "():()->anything OK")
        chkMemberEx("data<int>", "():(T)->unit", "", "():(int)->unit OK")
        chkMemberEx("data<-int>", "():(T)->unit", "", "():(nothing)->unit OK")
        chkMemberEx("data<+int>", "():(T)->unit", "", "():(int)->unit OK")
    }

    @Test fun testReturnConsumerSupplier() {
        chkMemberEx("data<int>", "():consumer<T>", "", "():consumer<int> OK")
        chkMemberEx("data<-int>", "():consumer<T>", "", "():consumer<nothing> OK")
        chkMemberEx("data<+int>", "():consumer<T>", "", "():consumer<int> OK")
        chkMemberEx("data<int>", "():supplier<T>", "", "():supplier<int> OK")
        chkMemberEx("data<-int>", "():supplier<T>", "", "():supplier<int> OK")
        chkMemberEx("data<+int>", "():supplier<T>", "", "():supplier<anything> OK")
    }

    @Test fun testTypeParamWildcard() {
        chkGlobal("<T>(list<T>):list<T>", "list<int>", "list<int> [T=int]")
        chkGlobal("<T>(list<T>):list<T>", "list<-int>", "list<-int> [T=CAP<-int>]")
        chkGlobal("<T>(list<T>):list<T>", "list<+int>", "list<+int> [T=CAP<+int>]")

        chkGlobal("<T>(list<set<T>>):set<T>", "list<set<int>>", "set<int> [T=int]")
        chkGlobal("<T>(list<set<T>>):set<T>", "list<set<-int>>", "n/a")
        chkGlobal("<T>(list<set<T>>):set<T>", "list<set<+int>>", "n/a")
    }

    @Test fun testTypeParamWildcardResult() {
        chkGlobal("<T>():list<T>", ":list<int>", "list<int> [T=int]")
        chkGlobal("<T>():list<T>", ":list<-int>", "list<int> [T=int]")
        chkGlobal("<T>():list<T>", ":list<+int>", "list<int> [T=int]")

        chkGlobal("<T>():list<-T>", ":list<int>", "unresolved:T")
        chkGlobal("<T>():list<-T>", ":list<-int>", "list<-int> [T=int]")
        chkGlobal("<T>():list<-T>", ":list<+int>", "unresolved:T")

        chkGlobal("<T>():list<+T>", ":list<int>", "unresolved:T")
        chkGlobal("<T>():list<+T>", ":list<-int>", "unresolved:T")
        chkGlobal("<T>():list<+T>", ":list<+int>", "list<+int> [T=int]")
    }

    @Test fun testWildcardVsWildcardGlobal() {
        chkGlobalEx("<T>(data<T>):data<T>", "data<int>", "[T=int] (a:data<int>):data<int>")
        chkGlobalEx("<T>(data<T>):data<T>", "data<-int>", "[T=CAP<-int>] (a:data<CAP<-int>>):data<-int>")
        chkGlobalEx("<T>(data<T>):data<T>", "data<+int>", "[T=CAP<+int>] (a:data<CAP<+int>>):data<+int>")
        chkGlobalEx("<T>(data<-T>):data<T>", "data<int>", "[T=int] (a:data<-int>):data<int>")
        chkGlobalEx("<T>(data<-T>):data<T>", "data<-int>", "[T=CAP<-int>] (a:data<-CAP<-int>>):data<-int>")
        chkGlobalEx("<T>(data<-T>):data<T>", "data<+int>", "[T=CAP<+int>] (a:data<-CAP<+int>>):data<+int>")
        chkGlobalEx("<T>(data<+T>):data<T>", "data<int>", "[T=int] (a:data<+int>):data<int>")
        chkGlobalEx("<T>(data<+T>):data<T>", "data<-int>", "[T=CAP<-int>] (a:data<+CAP<-int>>):data<-int>")
        chkGlobalEx("<T>(data<+T>):data<T>", "data<+int>", "[T=CAP<+int>] (a:data<+CAP<+int>>):data<+int>")

        chkGlobalEx("<T>(data<T>):data<-T>", "data<int>", "[T=int] (a:data<int>):data<-int>")
        chkGlobalEx("<T>(data<T>):data<-T>", "data<-int>", "[T=CAP<-int>] (a:data<CAP<-int>>):data<-int>")
        chkGlobalEx("<T>(data<T>):data<-T>", "data<+int>", "[T=CAP<+int>] (a:data<CAP<+int>>):data<*>")
        chkGlobalEx("<T>(data<T>):data<+T>", "data<int>", "[T=int] (a:data<int>):data<+int>")
        chkGlobalEx("<T>(data<T>):data<+T>", "data<-int>", "[T=CAP<-int>] (a:data<CAP<-int>>):data<*>")
        chkGlobalEx("<T>(data<T>):data<+T>", "data<+int>", "[T=CAP<+int>] (a:data<CAP<+int>>):data<+int>")
    }

    @Test fun testWildcardVsWildcardMemberParam() {
        chkMemberEx("data<int>", "(data<T>):unit", "nothing", "(a:data<int>):unit OK")
        chkMemberEx("data<int>", "(data<-T>):unit", "nothing", "(a:data<-int>):unit OK")
        chkMemberEx("data<int>", "(data<+T>):unit", "nothing", "(a:data<+int>):unit OK")
        chkMemberEx("data<-int>", "(data<T>):unit", "nothing", "(a:data<CAP<-int>>):unit OK")
        chkMemberEx("data<-int>", "(data<-T>):unit", "nothing", "(a:data<-CAP<-int>>):unit OK")
        chkMemberEx("data<-int>", "(data<+T>):unit", "nothing", "(a:data<+CAP<-int>>):unit OK")
        chkMemberEx("data<+int>", "(data<T>):unit", "nothing", "(a:data<CAP<+int>>):unit OK")
        chkMemberEx("data<+int>", "(data<-T>):unit", "nothing", "(a:data<-CAP<+int>>):unit OK")
        chkMemberEx("data<+int>", "(data<+T>):unit", "nothing", "(a:data<+CAP<+int>>):unit OK")
    }

    @Test fun testWildcardVsWildcardMemberResult() {
        chkMemberEx("data<int>", "():data<T>", "", "():data<int> OK")
        chkMemberEx("data<int>", "():data<-T>", "", "():data<-int> OK")
        chkMemberEx("data<int>", "():data<+T>", "", "():data<+int> OK")
        chkMemberEx("data<-int>", "():data<T>", "", "():data<-int> OK")
        chkMemberEx("data<-int>", "():data<-T>", "", "():data<-int> OK")
        chkMemberEx("data<-int>", "():data<+T>", "", "():data<*> OK")
        chkMemberEx("data<+int>", "():data<T>", "", "():data<+int> OK")
        chkMemberEx("data<+int>", "():data<-T>", "", "():data<*> OK")
        chkMemberEx("data<+int>", "():data<+T>", "", "():data<+int> OK")
    }

    @Test fun testMultipleWildcardArguments() {
        chkGlobalEx("<T>(data<T>,data<T>):unit", "data<int>,data<int>", "[T=int] (a:data<int>,b:data<int>):unit")
        chkGlobalEx("<T>(data<T>,data<T>):unit", "data<-int>,data<int>", "n/a")
        chkGlobalEx("<T>(data<T>,data<T>):unit", "data<+int>,data<int>", "n/a")
        chkGlobalEx("<T>(data<T>,data<T>):unit", "data<*>,data<int>", "n/a")

        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<int>,data<int>", "[T=int] (a:data<-int>,b:data<int>):unit")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<-int>,data<int>", "[T=int] (a:data<-int>,b:data<int>):unit")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<+int>,data<int>", "n/a")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<*>,data<int>", "n/a")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<int>,data<-int>", "n/a")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<int>,data<+int>",
            "[T=CAP<+int>] (a:data<-CAP<+int>>,b:data<CAP<+int>>):unit")
        chkGlobalEx("<T>(data<-T>,data<T>):unit", "data<int>,data<*>", "n/a")

        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<int>,data<int>", "[T=int] (a:data<+int>,b:data<int>):unit")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<-int>,data<int>", "n/a")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<+int>,data<int>", "[T=int] (a:data<+int>,b:data<int>):unit")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<*>,data<int>", "n/a")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<int>,data<-int>",
            "[T=CAP<-int>] (a:data<+CAP<-int>>,b:data<CAP<-int>>):unit")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<int>,data<+int>", "n/a")
        chkGlobalEx("<T>(data<+T>,data<T>):unit", "data<int>,data<*>", "n/a")
    }
}
