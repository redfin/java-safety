package com.redfin.nullSafe;

import org.junit.Test;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Test class for {@link NullSafe}
 */
public class NullSafeTest {

    @Test
    public void testThat_firstNullHandlerCanThrowException() {
        assertThrows("objectA must be non-null", IllegalArgumentException.class, () -> {
            ClassA nullObjectA = null;
            NullSafe.from(nullObjectA)
                    .ifNull(() -> { throw new IllegalArgumentException("objectA must be non-null"); })
                    .get();
        });
    }

    @Test
    public void testThat_firstNullHandlerCanReturnNonNull() {
        ClassA nullObjectA = null;
        ClassA backupClassA = new ClassA("backup");
        ClassA result = NullSafe.from(nullObjectA)
                .ifNull(() -> backupClassA)
                .get();
        assertThat(result, is(backupClassA));
    }

    @Test
    public void testThat_noNullsReturnExpectedValue() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE objectE = new ClassE("testObjectE");
        objectA.getB().getC().getD().setE(objectE);
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(objectE));
    }

    @Test
    public void testThat_firstNullHandlerPersistsNonNullValueToEnd() {
        ClassA nullObjectA = null;
        ClassA backupObjectA = new ClassA("backupObjectA");
        ClassE objectE = new ClassE("testObjectE");
        backupObjectA.getB().getC().getD().setE(objectE);
        ClassE result = NullSafe.from(nullObjectA)
                .ifNull(() -> backupObjectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(objectE));
    }

    @Test
    public void testThat_finalNullHandlerCatchesFirstNullValue() {
        ClassA nullObjectA = null;
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(nullObjectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void testThat_finalNullHandlerCatchesSecondNullValue() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getNullB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void testThat_finalNullHandlerCatchesThirdNullValue() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getNullC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void testThat_finalNullHandlerCatchesFourthNullValue() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getNullD)
                .access(ClassD::getE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void testThat_finalNullHandlerCatchesFifthNullValue() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getNullE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void test_SecondNullCheck() {
        ClassA objectA = new ClassA("testObjectA");
        ClassB backupObjectB = new ClassB("backupObjectB");
        ClassE backupObjectE = new ClassE("backupObjectE");
        backupObjectB.getC().getD().setE(backupObjectE);
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getNullB)
                .ifNull(() -> backupObjectB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void test_ThirdNullCheck() {
        ClassA objectA = new ClassA("testObjectA");
        ClassC backupObjectC = new ClassC("backupObjectC");
        ClassE backupObjectE = new ClassE("backupObjectE");
        backupObjectC.getD().setE(backupObjectE);
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getNullC)
                .ifNull(() -> backupObjectC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void test_FourthNullCheck() {
        ClassA objectA = new ClassA("testObjectA");
        ClassD backupObjectD = new ClassD("backupObjectD");
        ClassE backupObjectE = new ClassE("backupObjectE");
        backupObjectD.setE(backupObjectE);
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getNullD)
                .ifNull(() -> backupObjectD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void test_FifthNullCheck() {
        ClassA objectA = new ClassA("testObjectA");
        ClassE backupObjectE = new ClassE("backupObjectE");
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getNullE)
                .ifNull(() -> backupObjectE)
                .get();
        assertThat(result, is(backupObjectE));
    }

    @Test
    public void testThat_NullFallsThroughSafely() {
        ClassA objectA = null;
        ClassE result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .access(ClassB::getC)
                .access(ClassC::getD)
                .access(ClassD::getE)
                .get();
        assertThat(result, is(nullValue()));
    }

    @Test
    public void testThat_SourceDataOptionalGetUnwrapped() {
        ClassA objectA = new ClassA("testObjectA");
        ClassB result = NullSafe.from(Optional.of(objectA))
                .access(ClassA::getB)
                .get();
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void testThat_FirstAccessOptionalGetsUnwrapped() {
        ClassA objectA = new ClassA("testObjectA");
        ClassC result = NullSafe.from(objectA)
                .accessAndUnwrapOptional(ClassA::getOptionalB)
                .access(ClassB::getC)
                .get();
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void testThat_ChainedOptionalGetsUnwrapped() {
        ClassA objectA = new ClassA("testObjectA");
        ClassD result = NullSafe.from(objectA)
                .access(ClassA::getB)
                .accessAndUnwrapOptional(ClassB::getOptionalC)
                .access(ClassC::getD)
                .get();
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void testThat_toOptionalReturnsANonNullOptional() {
        ClassA objectA = new ClassA("testObjectA");
        Optional<ClassB> resultOptional = NullSafe.from(objectA)
                .access(ClassA::getB)
                .toOptional();
        assertTrue(resultOptional.isPresent());
    }

    @Test
    public void testThat_toOptionalReturnsANullOptional() {
        ClassA objectA = new ClassA("testObjectA");
        Optional<ClassB> resultOptional = NullSafe.from(objectA)
                .access(ClassA::getNullB)
                .toOptional();
        assertFalse(resultOptional.isPresent());
    }

    /**
     * Tests that a backup VALUE rather than a SUPPLIER works as expected
     */
    @Test
    public void testThat_ifNullValueWorks() {
        ClassA objectA = new ClassA("testObjectA");
        ClassB backupObjectB = new ClassB("backupObjectB");
        ClassB result = NullSafe.from(objectA)
                .access(ClassA::getNullB)
                .ifNull(backupObjectB)
                .get();
        assertThat(result, is(backupObjectB));
    }

    private class ClassE {
        private final String identifier;

        private ClassE(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassE classE = (ClassE) o;
            return Objects.equals(identifier, classE.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier);
        }
    }

    private class ClassD {
        private ClassE e = new ClassE("default");
        private final String identifier;

        private ClassD(String identifier) {
            this.identifier = identifier;
        }

        private void setE(ClassE e) {
            this.e = e;
        }

        private ClassE getE() {
            return e;
        }

        private ClassE getNullE() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassD classD = (ClassD) o;
            return Objects.equals(e, classD.e) &&
                    Objects.equals(identifier, classD.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(e, identifier);
        }
    }

    private class ClassC {
        private ClassD d = new ClassD("default");
        private final String identifier;

        private ClassC(String identifier) {
            this.identifier = identifier;
        }

        private ClassD getD() {
            return d;
        }

        private ClassD getNullD() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassC classC = (ClassC) o;
            return Objects.equals(d, classC.d) &&
                    Objects.equals(identifier, classC.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(d, identifier);
        }
    }

    private class ClassB {
        private ClassC c = new ClassC("default");
        private final String identifier;

        private ClassB(String identifier) {
            this.identifier = identifier;
        }

        private ClassC getC() {
            return c;
        }

        private Optional<ClassC> getOptionalC() {
            return Optional.of(c);
        }

        private ClassC getNullC() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassB classB = (ClassB) o;
            return Objects.equals(c, classB.c) &&
                    Objects.equals(identifier, classB.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(c, identifier);
        }
    }

    private class ClassA {
        private ClassB b = new ClassB("default");
        private final String identifier;

        private ClassA(String identifier) {
            this.identifier = identifier;
        }

        private ClassB getB() {
            return b;
        }

        private Optional<ClassB> getOptionalB() {
            return Optional.of(b);
        }

        private ClassB getNullB() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassA classA = (ClassA) o;
            return Objects.equals(b, classA.b) &&
                    Objects.equals(identifier, classA.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(b, identifier);
        }
    }
}
