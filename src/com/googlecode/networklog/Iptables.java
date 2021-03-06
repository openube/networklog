/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class Iptables {
  public static final String[] CELL_INTERFACES = {
    "rmnet+", "ppp+", "pdp+", "pnp+", "rmnet_sdio+", "uwbr+", "wimax+", "vsnet+", "usb+", "ccmni+"
  };

  public static final String[] WIFI_INTERFACES = {
    "eth+", "wlan+", "tiwlan+", "athwlan+", "ra+"
  };

  public static boolean addRules(Context context) {
    String iptablesBinary = SysUtils.getIptablesBinary();
    if(iptablesBinary == null) {
      return false;
    }

    if(checkRules(context) == true) {
      removeRules(context);
    }

    synchronized(NetworkLog.SCRIPT) {
      String scriptFile = context.getFilesDir().getAbsolutePath() + File.separator + NetworkLog.SCRIPT;
      String iptables  = context.getFilesDir().getAbsolutePath() + File.separator + iptablesBinary;

      try {
        PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));

        for(String iface : CELL_INTERFACES) {
          script.println(iptables + " -I OUTPUT 1 -o " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");

          script.println(iptables + " -I INPUT 1 -i " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");
        }

        for(String iface : WIFI_INTERFACES) {
          script.println(iptables + " -I OUTPUT 1 -o " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");

          script.println(iptables + " -I INPUT 1 -i " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");
        }

        script.flush();
        script.close();
      } catch(java.io.IOException e) {
        Log.e("NetworkLog", "addRules error", e);
      }

      String error = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "addRules").start(true);

      if(error != null) {
        SysUtils.showError(context, "Add rules error", error);
        return false;
      }
    }

    return true;
  }

  public static boolean removeRules(Context context) {
    String iptablesBinary = SysUtils.getIptablesBinary();
    if(iptablesBinary == null) {
      return false;
    }

    String iptables  = context.getFilesDir().getAbsolutePath() + File.separator + iptablesBinary;
    int tries = 0;

    while(checkRules(context) == true) {
      synchronized(NetworkLog.SCRIPT) {
        String scriptFile = context.getFilesDir().getAbsolutePath() + File.separator + NetworkLog.SCRIPT;

        try {
          PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));

          for(String iface : CELL_INTERFACES) {
            script.println(iptables + " -D OUTPUT -o " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");

            script.println(iptables + " -D INPUT -i " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");
          }

          for(String iface : WIFI_INTERFACES) {
            script.println(iptables + " -D OUTPUT -o " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");

            script.println(iptables + " -D INPUT -i " + iface + " -j LOG --log-prefix \"{NL}\" --log-uid");
          }

          script.flush();
          script.close();
        } catch(java.io.IOException e) {
          Log.e("NetworkLog", "removeRules error", e);
        }

        String error = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "removeRules").start(true);

        if(error != null) {
          SysUtils.showError(context, "Remove rules error", error);
          return false;
        }

        tries++;

        if(tries > 3) {
          MyLog.d("Too many attempts to remove rules, moving along...");
          return false;
        }
      }
    }

    return true;
  }

  public static boolean checkRules(Context context) {
    String iptablesBinary = SysUtils.getIptablesBinary();
    if(iptablesBinary == null) {
      return false;
    }

    String iptables  = context.getFilesDir().getAbsolutePath() + File.separator + iptablesBinary;

    synchronized(NetworkLog.SCRIPT) {
      String scriptFile = context.getFilesDir().getAbsolutePath() + File.separator + NetworkLog.SCRIPT;

      try {
        PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));
        script.println(iptables + " -L");
        script.flush();
        script.close();
      } catch(java.io.IOException e) {
        Log.e("NetworkLog", "checkRules error", e);
      }

      ShellCommand command = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "checkRules");
      String error = command.start(false);

      if(error != null) {
        SysUtils.showError(context, "Check rules error", error);
        return false;
      }

      StringBuilder result = new StringBuilder();

      while(true) {
        String line = command.readStdoutBlocking();

        if(line == null) {
          break;
        }

        result.append(line);
      }

      if(result == null) {
        return true;
      }

      command.checkForExit();
      if(command.exit != 0) {
        SysUtils.showError(context, "Check rules error", result.toString());
        return false;
      }

      MyLog.d("checkRules result: [" + result + "]");

      if(result.indexOf("Perhaps iptables or your kernel needs to be upgraded", 0) != -1) {
        SysUtils.showError(context, "Iptables error", "Iptables is not supported by this version of Android. Upgrade the kernel with the netfilter module or install a supported version of Android such as CyanogenMod.");
        return false;
      }

      return result.indexOf("{NL}", 0) == -1 ? false : true;
    }
  }
}
