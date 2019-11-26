package com.zeroturnaround.jrebel;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugWrapper extends HandlerWrapper {

  private static Logger log = LoggerFactory.getLogger(DebugWrapper.class);

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    long start = System.currentTimeMillis();
    try {
      super.handle(target, baseRequest, request, response);
    }
    finally {
      long duration = System.currentTimeMillis() - start;
      if (duration > SECONDS.toMillis(30)) {
        log.warn(request.getPathInfo() + " from " + getRemote(request) + " took " + duration + "ms");
      }
    }
  }

  private String getRemote(HttpServletRequest request) {
    String forward = request.getHeader("X-Forwarded-For");
    return forward != null ? forward : request.getRemoteAddr();
  }
}
