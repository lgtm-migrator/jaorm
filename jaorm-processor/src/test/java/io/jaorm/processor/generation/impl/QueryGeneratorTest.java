package io.jaorm.processor.generation.impl;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import io.jaorm.processor.JaormProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.JavaFileObject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

class QueryGeneratorTest extends CompilerTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"QueryWithSelect.java", "QueryWithSelectOptional.java", "QueryWithSelectList.java", "QueryWithSelectPrimitive.java"})
    void should_compile_queries_with_select(String fileName) {
        Compilation compilation = Compiler.javac()
                .withProcessors(new JaormProcessor())
                .compile(getFile("queries", fileName));
        checkCompilation(compilation);
    }

    @Test
    void should_compile_queries_with_update() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new JaormProcessor())
                .compile(getFile("queries", "QueryWithUpdate.java"));
        checkCompilation(compilation);
    }

    @Test
    void should_compile_queries_with_delete() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new JaormProcessor())
                .compile(getFile("queries", "QueryWithDelete.java"));
        checkCompilation(compilation);
    }

    @ParameterizedTest
    @MethodSource("getCompiled")
    void should_compile_queries(String message, List<JavaFileObject> files) {
        Compilation compilation = Compiler.javac()
                .withProcessors(new JaormProcessor())
                .compile(files);
        checkCompilation(compilation, message);
    }

    public static Stream<Arguments> getCompiled() {
        return Stream.of(
                Arguments.arguments("should_compile_queries_with_simple_dao", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithSelectEntity.java"))),
                Arguments.arguments("should_compile_queries_with_base_dao", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithBaseDao.java"))),
                Arguments.arguments("should_compile_queries_with_stream", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithStream.java"))),
                Arguments.arguments("should_compile_queries_with_stream_and_table_row", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithStreamAndTableRow.java"))),
                Arguments.arguments("should_compile_queries_with_table_row", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithTableRow.java"))),
                Arguments.arguments("should_compile_queries_with_base_dao", Arrays.asList(getFile("/entity/SimpleEntity.java"), getFile("/queries/QueryWithBaseDaoWithoutCustomMethods.java")))
        );
    }

}
