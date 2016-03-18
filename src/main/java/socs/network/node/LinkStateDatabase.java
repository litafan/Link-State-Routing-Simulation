package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import java.util.LinkedList;

import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */  
  public String getShortestPath(String destinationIP) {
    Set<String> Q = new HashSet<String>();
    HashMap<String, Integer> dist = new HashMap<String, Integer>();
    HashMap<String, String> prev = new HashMap<String, String>();
    for (String s : _store.keySet()) {
		if (_store.get(s).links.size() == 1) {
		} else {
			dist.put(s, Integer.MAX_VALUE);
			prev.put(s, null);
			Q.add(s);
		}
    }
    dist.put(rd.simulatedIPAddress, 0);
    while (!Q.isEmpty()) {
        String u = null;
        int minVal = Integer.MAX_VALUE;
        for (String s : Q) {
            if (dist.get(s) < minVal) {
                minVal = dist.get(s);
                u = s;
            }
        }
        Q.remove(u);
        for (LinkDescription l : _store.get(u).links) {
            if (!Q.contains(l.linkID)) {
                continue;
            } else {
                int alt = dist.get(u) + l.tosMetrics;
                if (alt < dist.get(l.linkID)) {
                    dist.put(l.linkID, alt);
                    prev.put(l.linkID, u);
                }
            }
        }
    }
    StringBuilder _sb = new StringBuilder();
    String u = destinationIP;
    if (prev.get(u) != null || u.equals(rd.simulatedIPAddress)) {
        _sb.insert(0, u);
    }
    while (prev.get(u) != null) {
        String v = prev.get(u);
        int length = 0;
        for (LinkDescription l : _store.get(v).links) {
            if (l.linkID.equals(u)) {
                length = l.tosMetrics;
            }
        }
        _sb.insert(0, " ->(" + length + ") ");
        _sb.insert(0, v);
        u = v;
    }
    return _sb.toString();
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
