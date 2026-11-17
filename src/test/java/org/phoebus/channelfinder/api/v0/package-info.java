/**
 * Tests of the v0 REST API.
 *
 * <p>The tests start the whole service and send the same HTTP requests that clients send. They
 * record what the API does today, so that a later change to it is deliberate and visible. Some of
 * what they record is surprising. It is kept as it is, because clients may depend on it. A comment
 * at each such test says what one would expect and what happens instead.
 *
 * <p>A test tagged {@code known-defect} and marked {@code @ExpectedToFail} asserts what the service
 * should do once a known bug is fixed. While the bug exists, the test fails and is reported as
 * skipped. If it starts to pass, the build fails and asks for the annotation to be removed. To fix
 * such a bug, remove the annotation, see the test fail, fix the bug, and see the test pass. {@code
 * -Dgroups=known-defect} runs only these tests.
 */
package org.phoebus.channelfinder.api.v0;
