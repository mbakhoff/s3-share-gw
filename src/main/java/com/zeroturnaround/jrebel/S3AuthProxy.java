package com.zeroturnaround.jrebel;

import org.eclipse.jetty.server.Server;

public class S3AuthProxy {

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("jetty.port", "8080"));
    Server server = new Server(port);
    server.setHandler(new SigningHandler());
    server.start();
    server.join();
  }
}
