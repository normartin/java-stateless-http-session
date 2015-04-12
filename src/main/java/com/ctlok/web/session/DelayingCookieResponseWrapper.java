package com.ctlok.web.session;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Call to addCookie is delayed if its name matches the given name.
 * Delay until servlet response is being returned
 */
class DelayingCookieResponseWrapper extends HttpServletResponseWrapper {

    private final String cookieName;
    private Cookie cookie;

    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @param response
     * @param cookieName name of the cookie that will be delayed
     * @throws IllegalArgumentException if the response is null or cookieName null/empty
     */
    public DelayingCookieResponseWrapper(HttpServletResponse response, String cookieName) {
        super(response);
        if(cookieName == null ||"".equals(cookieName)){
            throw new IllegalArgumentException("cookie name must be set");
        }
        this.cookieName = cookieName;
    }

    private void applyDelayedCookie() throws IOException {
        if(cookie != null){
            super.addCookie(cookie);
        }
    }

    @Override
    public void addCookie(Cookie cookie) {
        if(cookieName.equals(cookie.getName()) && !isCommitted()){
            this.cookie = cookie;
        }else {
            super.addCookie(cookie);
        }
    }

    /**
     * {@inheritDoc} javax.servlet.ServletResponseWrapper#flushBuffer()
     */
    @Override
    public void flushBuffer() throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();
        super.flushBuffer();
    }

    /**
     * {@inheritDoc} javax.servlet.ServletResponseWrapper#getOutputStream()
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();
        return super.getOutputStream();
    }

    /**
     * {@inheritDoc} javax.servlet.ServletResponseWrapper#getWriter()
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();

        return super.getWriter();
    }

    /**
     * {@inheritDoc}
     * javax.servlet.http.HttpServletResponseWrapper#sendError(int)
     */
    @Override
    public void sendError(int sc) throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();
        super.sendError(sc);
    }

    /**
     * {@inheritDoc}
     * javax.servlet.http.HttpServletResponseWrapper#sendError(int,
     * java.lang.String)
     */
    @Override
    public void sendError(int sc, String msg) throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();
        super.sendError(sc, msg);
    }

    /**
     * {@inheritDoc}
     * javax.servlet.http.HttpServletResponseWrapper#sendRedirect(java.lang.
     * String)
     */
    @Override
    public void sendRedirect(String location) throws IOException {
        // set cookie before sending the response
        applyDelayedCookie();
        super.sendRedirect(location);
    }

}
