import com.ctlok.web.session.StatelessSessionFilter;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.*;

public class StatelessSessionFilterTest {

    private StatelessSessionFilter filter;
    private CapturingFilterChain chain;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void before() throws Exception {

        chain = new CapturingFilterChain();

        FilterConfig config = createNiceMock(FilterConfig.class);
        expect(config.getInitParameter("HMAC_SHA1_KEY")).andReturn("key");
        replay(config);

        filter = new StatelessSessionFilter();
        filter.init(config);

        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        response.flushBuffer();

        EasyMock.expectLastCall().anyTimes();
        expect(response.isCommitted()).andReturn(false).anyTimes();
    }

    @Test
    // TODO is this behaviour ok?
    public void getSessionDoesNotCreateSessionCookieWithEmptySession() throws ServletException, IOException {

        expect(request.getCookies()).andReturn(null).times(2);

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();

        // creates a session
        assertThat(wrappedRequest.getSession()).isNotNull();

        // addCookie has not been called
        verify(response);
        verify(request);
    }


    @Test
    public void getSessionCreatesSessionCookieWithNoneEmptySession() throws ServletException, IOException {

        expect(request.getCookies()).andReturn(null).times(2);

        Capture<Cookie> capture = Capture.newInstance();
        response.addCookie(capture(capture));
        expectLastCall().once();

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(false)).isNull();
        assertThat(wrappedRequest.getSession()).isNotNull();
        assertThat(wrappedRequest.getSession(false)).isNotNull();

        wrappedRequest.getSession().setAttribute("1", "2");

        chain.flushResponse();
        verify(response);
        verify(request);

    }

    @Test
    public void canRestoreSessionFromValidCookie() throws ServletException, IOException {

        final Cookie validCookie = new Cookie("SESSION", "{\"1\":\"2\",\"__ct\":\"1428745523130\",\"__id\":\"4o79716jvg9k3b80v4rfbgvag8\",\"__s\":\"aa369e7e2fc7ced78b3143af1439527172d4c80b\"}");

        expect(request.getCookies()).andReturn(new Cookie[]{validCookie}).times(2);

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();

        assertThat(wrappedRequest.getSession(false)).isNotNull();
        assertThat(wrappedRequest.getSession(false).getAttribute("1")).isEqualTo("2");

        verify(response);
        verify(request);
    }

    @Test
    /**
     * current behavior is that a new session is created, if the signature does not match
     */
    public void canNotRestoreSessionWithAlteredSignature() throws ServletException, IOException {

        // changed first letter of __S
        final Cookie wrongCookie = new Cookie("SESSION", "{\"1\":\"2\",\"__ct\":\"1428745523130\",\"__id\":\"4o79716jvg9k3b80v4rfbgvag8\",\"__s\":\"ba369e7e2fc7ced78b3143af1439527172d4c80b\"}");

        expect(request.getCookies()).andReturn(new Cookie[]{wrongCookie}).anyTimes();

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(false)).isNotNull();
        assertThat(wrappedRequest.getSession(false).getAttribute("1")).isNull();

        verify(response);
        verify(request);
    }

    @Test
    /**
     * current behavior is that a new session is created, if the signature does not match
     */
    public void canNotRestoreSessionWithChangedValue() throws ServletException, IOException {

        // changed "1":"2" to "1":"1"
        final Cookie wrongCookie = new Cookie("SESSION", "{\"1\":\"1\",\"__ct\":\"1428745523130\",\"__id\":\"4o79716jvg9k3b80v4rfbgvag8\",\"__s\":\"aa369e7e2fc7ced78b3143af1439527172d4c80b\"}");

        expect(request.getCookies()).andReturn(new Cookie[]{wrongCookie}).anyTimes();

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(false)).isNotNull();
        assertThat(wrappedRequest.getSession(false).getAttribute("1")).isNull();

        verify(response);
        verify(request);
    }

    @Test
    public void multipleMutatingCallsToSessionResultsInOneCallToAddCookie() throws ServletException, IOException {

        expect(request.getCookies()).andReturn(new Cookie[]{}).anyTimes();

        response.addCookie(anyObject(Cookie.class));
        expectLastCall().times(1);

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(true)).isNotNull();
        wrappedRequest.getSession().setAttribute("1", "2");
        wrappedRequest.getSession().setAttribute("1", "2");
        wrappedRequest.getSession().setAttribute("1", "2");

        chain.flushResponse();
        verify(response);
        verify(request);

    }

    @Test
    public void canInvalidateSessionSetsMaxAgeToZero() throws ServletException, IOException {

        expect(request.getCookies()).andReturn(new Cookie[]{}).anyTimes();

        Capture<Cookie> cookieCapture = Capture.newInstance(CaptureType.LAST);

        response.addCookie(capture(cookieCapture));
        expectLastCall().times(1);

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(true)).isNotNull();

        final String content = "--2--";

        wrappedRequest.getSession().setAttribute("1", content);
        wrappedRequest.getSession().invalidate();

        chain.flushResponse();
        verify(response);
        verify(request);

        Cookie lastCookie = cookieCapture.getValue();

        assertThat(lastCookie.getMaxAge()).isEqualTo(0);
    }

    @Test
    public void invalidateRemovesAllAttributes() throws ServletException, IOException {

        expect(request.getCookies()).andReturn(new Cookie[]{}).anyTimes();

        Capture<Cookie> cookieCapture = Capture.newInstance();

        response.addCookie(capture(cookieCapture));
        expectLastCall().times(1);

        replay(request);
        replay(response);

        filter.doFilter(request, response, chain);

        final HttpServletRequest wrappedRequest = chain.getRequest();
        assertThat(wrappedRequest.getSession(true)).isNotNull();

        final String content = "--2--";

        wrappedRequest.getSession().setAttribute("1", content);
        wrappedRequest.getSession().invalidate();

        chain.flushResponse();
        verify(response);
        verify(request);

        final Cookie cookie =cookieCapture.getValue();
        assertThat(cookie.getValue()).doesNotContain(content);
        assertThat(cookie.getMaxAge()).isEqualTo(0);
    }


    private final static class CapturingFilterChain implements FilterChain {

        private ServletRequest servletRequest;
        private ServletResponse servletResponse;

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException, ServletException {
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }

        public HttpServletRequest getRequest() {
            return (HttpServletRequest) servletRequest;
        }

        public void flushResponse(){
            try {
                servletResponse.flushBuffer();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
    }

}
