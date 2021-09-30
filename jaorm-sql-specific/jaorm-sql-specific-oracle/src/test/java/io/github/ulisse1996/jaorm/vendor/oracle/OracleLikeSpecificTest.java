package io.github.ulisse1996.jaorm.vendor.oracle;

import io.github.ulisse1996.jaorm.vendor.specific.LikeSpecific;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OracleLikeSpecificTest {

    private final OracleLikeSpecific testSubject = new OracleLikeSpecific();

    @ParameterizedTest
    @EnumSource(LikeSpecific.LikeType.class)
    void should_convert_like_type(LikeSpecific.LikeType type) {
        String expected = null;
        switch (type) {

            case FULL:
                expected = "'%' || ? || '%'";
                break;
            case START:
                expected = "'%' || ? ";
                break;
            case END:
                expected = " ? || '%'";
                break;
            default:
                Assertions.fail();
        }
        Assertions.assertEquals(expected, testSubject.convertToLikeSupport(type));
    }
}
