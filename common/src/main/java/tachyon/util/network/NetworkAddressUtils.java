/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.util.network;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.thrift.transport.TServerSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.conf.TachyonConf;
import tachyon.thrift.NetAddress;

/**
 * Common network address related utilities shared by all components in Tachyon.
 */
public final class NetworkAddressUtils {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);

  private static String sLocalHost;
  private static String sLocalIP;

  private NetworkAddressUtils() {}

  /**
   * Gets master host name. Try to retrieve user specified master host name. If the host name is not
   * explicitly specified, Tachyon will automatically select an appropriate host name.
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified master host name.
   */
  public static String getMasterHostName(TachyonConf conf) {
    return conf.get(Constants.MASTER_HOSTNAME, getLocalHostName(conf));
  }

  /**
   * Gets a binding hostname for master
   *
   * Host binding strategy on multihomed networking: 1) Environment variables via tachyon-env.sh or
   * from OS settings 2) Default properties via tachyon-default.properties file 3) A reachable local
   * host name for the host this JVM is running on
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified hostname ($TACHYON_MASTER_BIND_HOST) for binding.
   */
  public static String getMasterBindHost(TachyonConf conf) {
    return conf.get(Constants.MASTER_BIND_HOST, getLocalHostName(conf));
  }

  /**
   * Gets a binding hostname for master web server
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified hostname ($TACHYON_MASTER_WEB_BIND_HOST) for binding.
   */
  public static String getMasterWebBindHost(TachyonConf conf) {
    return conf.get(Constants.MASTER_WEB_BIND_HOST, getLocalHostName(conf));
  }

  /**
   * Helper method to get the {@link java.net.InetSocketAddress} connect hostname of the worker.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's connect hostname
   */
  public static String getWorkerConnectHost(TachyonConf conf) {
    if (conf.get(Constants.WORKER_BIND_HOST, "localhost").equals("0.0.0.0")) {
      return NetworkAddressUtils.getLocalHostName(conf);
    }
    return getWorkerBindHost(conf);
  }

  /**
   * Gets a binding hostname for worker
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified hostname ($TACHYON_WORKER_BIND_HOST) for binding.
   */
  public static String getWorkerBindHost(TachyonConf conf) {
    return conf.get(Constants.WORKER_BIND_HOST, getLocalHostName(conf));
  }

  /**
   * Gets a binding hostname for worker data server
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified hostname ($TACHYON_WORKER_DATA_BIND_HOST) for binding.
   */
  public static String getWorkerDataBindHost(TachyonConf conf) {
    return conf.get(Constants.WORKER_DATA_BIND_HOST, getLocalHostName(conf));
  }

  /**
   * Gets a binding hostname for worker web server
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the user specified hostname ($TACHYON_WORKER_WEB_BIND_HOST) for binding.
   */
  public static String getWorkerWebBindHost(TachyonConf conf) {
    return conf.get(Constants.WORKER_WEB_BIND_HOST, getLocalHostName(conf));
  }

  /**
   * Gets a local host name for the host this JVM is running on
   *
   * @param conf Tachyon configuration used to look up the host resolution timeout
   * @return the local host name, which is not based on a loopback ip address.
   */
  public static String getLocalHostName(TachyonConf conf) {
    if (sLocalHost != null) {
      return sLocalHost;
    }
    int hostResolutionTimeout = conf.getInt(Constants.HOST_RESOLUTION_TIMEOUT_MS,
        Constants.DEFAULT_HOST_RESOLUTION_TIMEOUT_MS);
    return getLocalHostName(hostResolutionTimeout);
  }

  /**
   * Gets a local host name for the host this JVM is running on
   *
   * @param timeout Timeout in milliseconds to use for checking that a possible local
   *                host is reachable
   * @return the local host name, which is not based on a loopback ip address.
   */
  public static String getLocalHostName(int timeout) {
    if (sLocalHost != null) {
      return sLocalHost;
    }

    try {
      sLocalHost = InetAddress.getByName(getLocalIpAddress(timeout)).getCanonicalHostName();
      return sLocalHost;
    } catch (UnknownHostException e) {
      LOG.error(e.getMessage(), e);
      throw Throwables.propagate(e);
    }
  }

  /**
   * Check if the underlying OS is Windows.
   */
  public static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

  /**
   * Gets a local IP address for the host this JVM is running on
   *
   * @param conf Tachyon configuration
   * @return the local ip address, which is not a loopback address and is reachable
   */
  public static String getLocalIpAddress(TachyonConf conf) {
    if (sLocalIP != null) {
      return sLocalIP;
    }
    int hostResolutionTimeout = conf.getInt(Constants.HOST_RESOLUTION_TIMEOUT_MS,
        Constants.DEFAULT_HOST_RESOLUTION_TIMEOUT_MS);
    return getLocalIpAddress(hostResolutionTimeout);
  }

  /**
   * Gets a local IP address for the host this JVM is running on
   *
   * @param timeout Timeout in milliseconds to use for checking that a possible local IP is
   *        reachable
   * @return the local ip address, which is not a loopback address and is reachable
   */
  public static String getLocalIpAddress(int timeout) {
    if (sLocalIP != null) {
      return sLocalIP;
    }

    try {
      InetAddress address = InetAddress.getLocalHost();
      LOG.debug("address: {} isLoopbackAddress: {}, with host {} {}", address,
          address.isLoopbackAddress(), address.getHostAddress(), address.getHostName());

      // Make sure that the address is actually reachable since in some network configurations
      // it is possible for the InetAddress.getLocalHost() call to return a non-reachable
      // address e.g. a broadcast address
      if (address.isAnyLocalAddress() || address.isLoopbackAddress()
          || !address.isReachable(timeout) || !(address instanceof Inet4Address)) {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

        // Make getNetworkInterfaces have the same order of network interfaces as listed on
        // unix-like systems. This optimization can help avoid to get some special addresses, such
        // as loopback address"127.0.0.1", virtual bridge address "192.168.122.1" as far as
        // possible.
        if (!WINDOWS) {
          List<NetworkInterface> netIFs = Collections.list(networkInterfaces);
          Collections.reverse(netIFs);
          networkInterfaces = Collections.enumeration(netIFs);
        }

        while (networkInterfaces.hasMoreElements()) {
          NetworkInterface ni = networkInterfaces.nextElement();
          Enumeration<InetAddress> addresses = ni.getInetAddresses();
          while (addresses.hasMoreElements()) {
            address = addresses.nextElement();

            // Address must not be link local or loopback. And it must be reachable
            if (!address.isLinkLocalAddress() && !address.isLoopbackAddress()
                && (address instanceof Inet4Address) && address.isReachable(timeout)) {
              sLocalIP = address.getHostAddress();
              return sLocalIP;
            }
          }
        }

        LOG.warn("Your hostname, " + InetAddress.getLocalHost().getHostName() + " resolves to"
            + " a loopback/non-reachable address: " + address.getHostAddress()
            + ", but we couldn't find any external IP address!");
      }

      sLocalIP = address.getHostAddress();
      return sLocalIP;
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
      throw Throwables.propagate(e);
    }
  }

  /**
   * Replace and resolve the hostname in a given address or path string.
   *
   * @param path an address or path string, e.g., "hdfs://host:port/dir", "file:///dir", "/dir".
   * @return an address or path string with hostname resolved, or the original path intact if no
   *         hostname is embedded, or null if the given path is null or empty.
   * @throws UnknownHostException if the hostname cannot be resolved.
   */
  public static TachyonURI replaceHostName(TachyonURI path) throws UnknownHostException {
    if (path == null) {
      return null;
    }

    if (path.hasAuthority() && path.getPort() != -1) {
      String authority = resolveHostName(path.getHost());
      if (path.getPort() != -1) {
        authority += ":" + path.getPort();
      }
      return new TachyonURI(path.getScheme(), authority, path.getPath());
    }
    return path;
  }

  /**
   * Resolve a given hostname by a canonical hostname. When a hostname alias (e.g., those specified
   * in /etc/hosts) is given, the alias may not be resolvable on other hosts in a cluster unless the
   * same alias is defined there. In this situation, loadufs would break.
   *
   * @param hostname the input hostname, which could be an alias.
   * @return the canonical form of the hostname, or null if it is null or empty.
   * @throws UnknownHostException if the given hostname cannot be resolved.
   */
  public static String resolveHostName(String hostname) throws UnknownHostException {
    if (hostname == null || hostname.isEmpty()) {
      return null;
    }

    return InetAddress.getByName(hostname).getCanonicalHostName();
  }

  /**
   * Get FQDN(Full Qualified Domain Name) from representations of network address in Tachyon, except
   * String representation which should be handled by #resolveHostName(String hostname) which will
   * handle the situation where hostname is null.
   *
   * @param addr the input network address representation, can not be null
   * @return the resolved FQDN host name
   */
  public static String getFqdnHost(InetSocketAddress addr) {
    return addr.getAddress().getCanonicalHostName();
  }

  public static String getFqdnHost(NetAddress addr) throws UnknownHostException {
    return resolveHostName(addr.getMHost());
  }

  /**
   * Gets the port for the underline socket. This function calls
   * {@link #getSocket(org.apache.thrift.transport.TServerSocket)}, so reflection will be
   * used to get the port.
   *
   * @see #getSocket(org.apache.thrift.transport.TServerSocket)
   */
  public static int getPort(TServerSocket thriftSocket) {
    return getSocket(thriftSocket).getLocalPort();
  }

  /**
   * Extracts the port from the thrift socket. As of thrift 0.9, the internal socket used is not
   * exposed in the API, so this function will use reflection to get access to it.
   *
   * @throws java.lang.RuntimeException if reflection calls fail
   */
  public static ServerSocket getSocket(final TServerSocket thriftSocket) {
    try {
      Field field = TServerSocket.class.getDeclaredField("serverSocket_");
      field.setAccessible(true);
      return (ServerSocket) field.get(thriftSocket);
    } catch (NoSuchFieldException e) {
      throw Throwables.propagate(e);
    } catch (IllegalAccessException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * Gets the Tachyon master address from the configuration
   *
   * @param conf the configuration of Tachyon
   * @return the InetSocketAddress of the master
   */
  public static InetSocketAddress getMasterAddress(TachyonConf conf) {
    String masterHost = conf.get(Constants.MASTER_HOSTNAME, getLocalHostName(conf));
    int masterPort = conf.getInt(Constants.MASTER_PORT, Constants.DEFAULT_MASTER_PORT);
    return new InetSocketAddress(masterHost, masterPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} connect address of the master.
   *
   * @param conf the configuration of Tachyon
   * @return the master's connect address
   */
  public static InetSocketAddress getMasterConnectAddress(TachyonConf conf) {
    if (conf.get(Constants.MASTER_BIND_HOST, "localhost").equals("0.0.0.0")) {
      String masterHostName = getMasterHostName(conf);
      int masterPort = conf.getInt(Constants.MASTER_PORT, Constants.DEFAULT_MASTER_PORT);
      TachyonConf.assertValidPort(masterPort, conf);
      return new InetSocketAddress(masterHostName, masterPort);
    }
    return getMasterBindAddress(conf);
  }

  /**
   * Gets the Tachyon master address from the configuration
   *
   * @param conf the configuration of Tachyon
   * @return the InetSocketAddress of the master
   */
  public static InetSocketAddress getMasterBindAddress(TachyonConf conf) {
    String masterBindHost = getMasterBindHost(conf);
    int masterPort = conf.getInt(Constants.MASTER_PORT, Constants.DEFAULT_MASTER_PORT);
    TachyonConf.assertValidPort(masterPort, conf);
    return new InetSocketAddress(masterBindHost, masterPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} connect address of the master.
   *
   * @param conf the configuration of Tachyon
   * @return the master's connect address
   */
  public static InetSocketAddress getMasterWebConnectAddress(TachyonConf conf) {
    if (conf.get(Constants.MASTER_WEB_BIND_HOST, "localhost").equals("0.0.0.0")) {
      String masterLocalhost = getMasterHostName(conf);
      int masterPort = conf.getInt(Constants.MASTER_WEB_PORT, Constants.DEFAULT_MASTER_WEB_PORT);
      return new InetSocketAddress(masterLocalhost, masterPort);
    }
    return getMasterWebBindAddress(conf);
  }

  /**
   * Gets the Tachyon master address from the configuration
   *
   * @param conf the configuration of Tachyon
   * @return the InetSocketAddress of the master
   */
  public static InetSocketAddress getMasterWebBindAddress(TachyonConf conf) {
    String masterWebBindlhost = getMasterWebBindHost(conf);
    int masterWebPort = conf.getInt(Constants.MASTER_WEB_PORT, Constants.DEFAULT_MASTER_WEB_PORT);
    TachyonConf.assertValidPort(masterWebPort, conf);
    return new InetSocketAddress(masterWebBindlhost, masterWebPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} of the worker.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's address
   */
  public static InetSocketAddress getWorkerAddress(TachyonConf conf) {
    String workerHost = getLocalHostName(conf);
    int workerPort = conf.getInt(Constants.WORKER_PORT, Constants.DEFAULT_WORKER_PORT);
    return new InetSocketAddress(workerHost, workerPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} connect address of the worker.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's connect address
   */
  public static InetSocketAddress getWorkerConnectAddress(TachyonConf conf) {
    if (conf.get(Constants.WORKER_BIND_HOST, "localhost").equals("0.0.0.0")) {
      String workerLocalhost = getLocalHostName(conf);
      int workerPort = conf.getInt(Constants.WORKER_PORT, Constants.DEFAULT_WORKER_PORT);
      return new InetSocketAddress(workerLocalhost, workerPort);
    }
    return getWorkerBindAddress(conf);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} of the worker bind address.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's address for binding
   */
  public static InetSocketAddress getWorkerBindAddress(TachyonConf conf) {
    String workerBindHost = getWorkerBindHost(conf);
    int workerPort = conf.getInt(Constants.WORKER_PORT, Constants.DEFAULT_WORKER_PORT);
    TachyonConf.assertValidPort(workerPort, conf);
    return new InetSocketAddress(workerBindHost, workerPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} connect address of the worker data server.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's data server connect address
   */
  public static InetSocketAddress getWorkerDataConnectAddress(TachyonConf conf) {
    if (conf.get(Constants.WORKER_BIND_HOST, "localhost").equals("0.0.0.0")) {
      String workerLocalhost = getLocalHostName(conf);
      int workerDataPort =
          conf.getInt(Constants.WORKER_DATA_PORT, Constants.DEFAULT_WORKER_DATA_SERVER_PORT);
      return new InetSocketAddress(workerLocalhost, workerDataPort);
    }
    return getWorkerDataBindAddress(conf);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} of bind address of the worker data server.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's data server address for binding
   */
  public static InetSocketAddress getWorkerDataBindAddress(TachyonConf conf) {
    String workerDataBindHost = getWorkerDataBindHost(conf);
    int workerDataPort =
        conf.getInt(Constants.WORKER_DATA_PORT, Constants.DEFAULT_WORKER_DATA_SERVER_PORT);
    TachyonConf.assertValidPort(workerDataPort, conf);
    return new InetSocketAddress(workerDataBindHost, workerDataPort);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} connect address of the worker data server.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's data server connect address
   */
  public static InetSocketAddress getWorkerWebConnectAddress(TachyonConf conf) {
    if (conf.get(Constants.WORKER_BIND_HOST, "localhost").equals("0.0.0.0")) {
      String workerLocalhost = getLocalHostName(conf);
      int workerWebPort = conf.getInt(Constants.WORKER_WEB_PORT, Constants.DEFAULT_WORKER_WEB_PORT);
      return new InetSocketAddress(workerLocalhost, workerWebPort);
    }
    return getWorkerWebBindAddress(conf);
  }

  /**
   * Helper method to get the {@link InetSocketAddress} of the worker web bind address.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's web bind address
   */
  public static InetSocketAddress getWorkerWebBindAddress(TachyonConf conf) {
    String workerWebBindHost = getWorkerWebBindHost(conf);
    int workerWebPort = conf.getInt(Constants.WORKER_WEB_PORT, Constants.DEFAULT_WORKER_WEB_PORT);
    TachyonConf.assertValidPort(workerWebPort, conf);
    return new InetSocketAddress(workerWebBindHost, workerWebPort);
  }

  /**
   * Gets the {@link java.net.InetSocketAddress} of the local worker.
   *
   * Make sure there is a local worker before calling this method.
   *
   * @param conf the configuration of Tachyon
   * @return the worker's address
   */
  public static InetSocketAddress getLocalWorkerAddress(TachyonConf conf) {
    String workerHostname = getLocalHostName(conf);
    // Cannot rely on tachyon-default.properties because GetMasterWorkerAddressTest will test with
    // fake conf
    int workerPort = conf.getInt(Constants.WORKER_PORT, Constants.DEFAULT_WORKER_PORT);
    return new InetSocketAddress(workerHostname, workerPort);
  }

  /**
   * Parse InetSocketAddress from a String
   *
   * @param address
   * @return InetSocketAddress of the String
   * @throws IOException
   */
  public static InetSocketAddress parseInetSocketAddress(String address) throws IOException {
    if (address == null) {
      return null;
    }
    String[] strArr = address.split(":");
    if (strArr.length != 2) {
      throw new IOException("Invalid InetSocketAddress " + address);
    }
    return new InetSocketAddress(strArr[0], Integer.parseInt(strArr[1]));
  }
}
