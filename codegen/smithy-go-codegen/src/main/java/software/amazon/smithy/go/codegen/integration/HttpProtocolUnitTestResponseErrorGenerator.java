/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 *
 */

package software.amazon.smithy.go.codegen.integration;

import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.go.codegen.GoDependency;
import software.amazon.smithy.go.codegen.GoWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

/**
 * Generates HTTP protocol unit tests for HTTP response API error test cases.
 */
public class HttpProtocolUnitTestResponseErrorGenerator extends HttpProtocolUnitTestResponseGenerator {
    protected final Shape errorShape;
    protected final Symbol errorSymbol;

    /**
     * Builder to lazy initialize the protocol test generator with operation specific configuration.
     */
    public static class Builder {
        /**
         * Builds the test generator for the provided configuration.
         *
         * @param model          Smithy model.
         * @param symbolProvider Smithy symbol provider.
         * @param protocolName   Name of the model's protocol that will be generated.
         * @param operation      shape for the Operation the unit test is generated for.
         * @param error          the error shape for the test cases.
         * @param testCases      Set of test cases for the operation.
         * @return initialize protocol test generator.
         */
        public HttpProtocolUnitTestResponseErrorGenerator build(
                Model model, SymbolProvider symbolProvider, String protocolName, OperationShape operation, Shape error,
                List<HttpResponseTestCase> testCases
        ) {
            return new HttpProtocolUnitTestResponseErrorGenerator(model, symbolProvider, protocolName, operation, error,
                    testCases);
        }
    }

    /**
     * Initializes the protocol test generator
     *
     * @param model          smithy model
     * @param symbolProvider symbol provider
     * @param protocolName   name of the protocol test is being generated for.
     * @param operation      operation shape the test is for.
     * @param testCases      test cases for the operation.
     */
    protected HttpProtocolUnitTestResponseErrorGenerator(
            Model model, SymbolProvider symbolProvider, String protocolName, OperationShape operation, Shape error,
            List<HttpResponseTestCase> testCases
    ) {
        super(model, symbolProvider, protocolName, operation, testCases);
        this.errorShape = error;
        this.errorSymbol = symbolProvider.toSymbol(errorShape);
    }

    /**
     * Provides the unit test function's format string.
     *
     * @return returns format string paired with unitTestFuncNameArgs
     */
    @Override
    protected String unitTestFuncNameFormat() {
        return "TestClient_$L_$L_$LDeserialize";
    }

    /**
     * Provides the unit test function name's format string arguments.
     *
     * @return returns a list of arguments used to format the unitTestFuncNameFormat returned format string.
     */
    @Override
    protected Object[] unitTestFuncNameArgs() {
        return new Object[]{opSymbol.getName(), errorSymbol.getName(), protocolName};
    }

    /**
     * Hook to generate the parameter declarations as struct parameters into the test case's struct definition.
     * Must generate all test case parameters before returning.
     *
     * @param writer writer to write generated code with.
     */
    @Override
    protected void generateTestCaseParams(GoWriter writer) {
        writer.write("StatusCode int");
        // TODO authScheme
        writer.addUseImports(GoDependency.NET_HTTP);

        writer.write("Header http.Header");
        // TODO why do these exist?
        // writer.write("RequireHeaders []string");
        // writer.write("ForbidHeaders []string");

        writer.write("BodyMediaType string");
        writer.write("Body []byte");

        // TODO vendorParams for requestID
        writer.write("ExpectError $P", errorSymbol);
    }

    /**
     * Hook to generate all the test case parameters as struct member values for a single test case.
     * Must generate all test case parameter values before returning.
     *
     * @param writer   writer to write generated code with.
     * @param testCase definition of a single test case.
     */
    @Override
    protected void generateTestCaseValues(GoWriter writer, HttpResponseTestCase testCase) {
        writeStructField(writer, "StatusCode", testCase.getCode());
        writeHeaderStructField(writer, "Header", testCase.getHeaders());

        testCase.getBodyMediaType().ifPresent(mediaType -> {
            writeStructField(writer, "BodyMediaType", "$S", mediaType);
        });
        testCase.getBody().ifPresent(body -> {
            writeStructField(writer, "Body", "[]byte(`$L`)", body);
        });

        writeStructField(writer, "ExpectError", errorShape, testCase.getParams());
    }

    /**
     * Hook to generate the body of the test that will be invoked for all test cases of this operation. Should not
     * do any assertions.
     *
     * @param writer writer to write generated code with.
     */
    @Override
    protected void generateTestAssertions(GoWriter writer) {
        writeAssertNotNil(writer, "err");
        writeAssertNil(writer, "result");

        // Operation Metadata
        writer.openBlock("var opErr interface{", "}", () -> {
            writer.write("Service() string");
            writer.write("Operation() string");
        });
        writer.addUseImports(GoDependency.ERRORS);
        writer.openBlock("if !errors.As(err, &opErr) {", "}", () -> {
            writer.write("t.Fatalf(\"expect $P operation error, got %T\", err)", errorSymbol);
        });
        writer.openBlock("if e, a := client.ServiceID(), opErr.Service(); e != a {", "}", () -> {
            writer.write("t.Errorf(\"expect %v operation service name, got %v\", e, a)");
        });
        writer.openBlock("if e, a := $S, opErr.Operation(); e != a {", "}", opSymbol.getName(), () -> {
            writer.write("t.Errorf(\"expect %v operation service name, got %v\", e, a)");
        });

        // Smithy API error
        writer.write("var actualErr $P", errorSymbol);
        writer.openBlock("if !errors.As(err, &actualErr) {", "}", () -> {
            writer.write("t.Fatalf(\"expect $P result error, got %T\", err)", errorSymbol);
        });

        writeAssertComplexEqual(writer, "c.ExpectError", "actualErr", new String[]{"middleware.Metadata{}"});

        // TODO assertion for protocol metadata
    }
}
