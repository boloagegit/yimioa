package com.redmoon.oa.flow;

import java.io.Serializable;
import java.sql.*;
import java.util.Calendar;
import java.util.Vector;

import cn.js.fan.db.Conn;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;
import com.redmoon.oa.db.SequenceManager;
import org.apache.log4j.Logger;
import com.cloudwebsoft.framework.util.LogUtil;
import cn.js.fan.util.DateUtil;
import com.redmoon.oa.oacalendar.OACalendarDb;

/**
 * workflow_link:2,1,,172,174,100,118,150,118,125,118,125,118,0,2005,05,02,新用户13,蓝风;
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class WorkflowLinkDb implements Serializable{
    String connname = "";
    final String INSERT = "insert into flow_link (id, flow_id, action_from, action_to,speedup_date,isSpeedup,title,type,cond_desc,item1,expire_hour,expire_action) values (?,?,?,?,?,?,?,?,?,?,?,?)";
    final String LOAD = "select flow_id,action_from,action_to,isSpeedup,speedup_date,title,type,cond_desc,item1,expire_hour,expire_action from flow_link where id=?";
    final String SAVE = "update flow_link set action_from=?,action_to=?,isSpeedup=?,speedup_date=?,title=?,type=?,cond_desc=?,item1=?,expire_hour=?,expire_action=? where id=?";
    final String DELETE = "delete from flow_link where id=?";

    public static final int TYPE_TOWARD = 0; // 流向
    public static final int TYPE_RETURN = 1; // 打回
    public static final int TYPE_BOTH = 2;   // 流向及打回

    private String expireAction;
    public static final String COND_TYPE_FORM = "";
    public static final String COND_TYPE_ROLE = "role";
    public static final String COND_TYPE_DEPT = "dept";
    public static final String COND_TYPE_COMB_COND = "comb_cond";
    public static final String COND_TYPE_NONE = "-1";

    public static final String COND_TYPE_SCRIPT = "script";

    public static final String COND_TYPE_MUST = "1";

    public WorkflowLinkDb() {
        init();
    }

    public WorkflowLinkDb(int id) {
        init();
        this.id = id;
        loadFromDb();
    }

    private String title;

    public void init() {
        flowId = -1;
        speedupDate = speedupDate.getInstance();
        connname = Global.getDefaultDB();
    }

    private Calendar speedupDate;

    public void setId(int id) {
        this.id = id;
    }

    public void setFlowId(int flowId) {
        this.flowId = flowId;
    }

    public void setSpeedupDate(Calendar speedupDate) {
        this.speedupDate = speedupDate;
    }

    public WorkflowActionDb getToAction() {
        WorkflowActionDb wa = new WorkflowActionDb();
        return wa.getWorkflowActionDbByInternalName(to, flowId);
    }

    /**
     * 找出从from至to节点之间的连接线（这根线有可能为连接线，也可能为打回线，也可能为both），或者to至from，但类型为both的连接线
     * 用于当需要notifyUser时，获取连接线上的expireHour
     * @param from WorkflowActionDb
     * @param to WorkflowActionDb
     * @return WorkflowLinkDb
     */
    public WorkflowLinkDb getWorkflowLinkDbForward(WorkflowActionDb from, WorkflowActionDb to) {
        String sql =
                "select id from flow_link where flow_id=? and action_from=? and action_to=?";
        // Based on the id in the object, get the message data from the database.
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, from.getFlowId());
            pstmt.setString(2, from.getInternalName());
            pstmt.setString(3, to.getInternalName());
            rs = conn.executePreQuery();
            if (rs != null) {
                if (rs.next()) {
                    int linkId = rs.getInt(1);
                    return getWorkflowLinkDb(linkId);
                }
            }

            pstmt.close();

            // 如果没有找到，则继续寻找为both型的从to至from的连接线
            sql = "select id from flow_link where flow_id=? and action_from=? and action_to=? and type=" + TYPE_BOTH;
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, from.getFlowId());
            pstmt.setString(2, to.getInternalName());
            pstmt.setString(3, from.getInternalName());
            rs = conn.executePreQuery();
            if (rs != null) {
                if (rs.next()) {
                    int linkId = rs.getInt(1);
                    return getWorkflowLinkDb(linkId);
                }
            }
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("getToLinks:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }

        return null;
    }

    /**
     * 获取从当前节点连出的连接线，除去打回线
     * @param curAction WorkflowActionDb
     * @return int
     */
    public Vector getToWorkflowLinks(WorkflowActionDb curAction) {
        Vector ret = new Vector();
        String sql =
                "select id from flow_link where action_from=? and flow_id=? and type<>" +
                WorkflowLinkDb.TYPE_RETURN;
        // Based on the id in the object, get the message data from the database.
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, curAction.getInternalName());
            pstmt.setInt(2, curAction.getFlowId());
            rs = conn.executePreQuery();
            if (rs != null) {
                while (rs.next()) {
                    int linkId = rs.getInt(1);
                    ret.addElement(getWorkflowLinkDb(linkId));
                }
            }
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("getToWorkflowLinks:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return ret;
    }
    
    /**
     * 获取从当前节点连出的连接线的数量即入度(除去返回线)，用于得到开始节点（多起点）fgf 20160924
     * @Description: 
     * @param curAction
     * @return
     */
    public int getFromLinkCount(WorkflowActionDb curAction) {
    	int ret = 0;
        String sql =
                "select count(id) from flow_link where action_to=? and flow_id=? and type<>" +
                WorkflowLinkDb.TYPE_RETURN;
        // Based on the id in the object, get the message data from the database.
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, curAction.getInternalName());
            pstmt.setInt(2, curAction.getFlowId());
            rs = conn.executePreQuery();
            if (rs != null) {
                while (rs.next()) {
                    ret = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("getFromWorkflowLinksCount:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return ret;
    }    

    public void setIsSpeedup(int isSpeedup) {
        this.isSpeedup = isSpeedup;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setCondDesc(String condDesc) {
        this.condDesc = condDesc;
    }

    public void setCondType(String condType) {
        this.condType = condType;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public void setExpireHour(double expireHour) {
        this.expireHour = expireHour;
    }

    public void setExpireAction(String expireAction) {
        this.expireAction = expireAction;
    }

    public int getId() {
        return id;
    }

    public int getFlowId() {
        return flowId;
    }

    public Calendar getSpeedupDate() {
        return speedupDate;
    }

    public int getIsSpeedup() {
        return isSpeedup;
    }

    public String getTo() {
        return to;
    }

    public String getFrom() {
        return from;
    }

    public String getTitle() {
        return title;
    }

    public int getType() {
        return type;
    }

    public String getCondDesc() {
        return condDesc;
    }

    public String getCondType() {
        return condType;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public double getExpireHour() {
        return expireHour;
    }

    public String getExpireAction() {
        return expireAction;
    }

    /**
     * workflow_link:2,1,,172,174,100,118,150,118,125,118,125,118,0,2005,05,02,新用户13,蓝风;
     * @param str String
     */
    public boolean fromString(String str) {
        if (!str.startsWith("workflow_link"))
            return false;
        int p = str.indexOf(":");
        int q = str.indexOf(";");
        if (p==-1 || q==-1) {
            Logger.getLogger(getClass()).info("格式错误！");
            return false;
        }
        str = str.substring(p+1, q);
        String[] ary = str.split("\\,");
        if (ary.length<18) {
            Logger.getLogger(getClass()).info("数组长度小于18！");
            return false;
        }
        isSpeedup = Integer.parseInt(ary[13]);
        if (isSpeedup==1) {
            int y = Integer.parseInt(ary[14]);
            int m = Integer.parseInt(ary[15]);
            int d = Integer.parseInt(ary[16]);
            speedupDate = speedupDate.getInstance();
            speedupDate.set(y, m - 1, d);
            // System.out.println("WorkflowLinkDb fromString y=" + y + " m=" + m + " d=" + d + " speedupDate=" + speedupDate);
        }
        title = tran(ary[2]);
        from = tran(ary[3]);
        to = tran(ary[4]);
        type = Integer.parseInt(ary[17]);

        if (ary.length>=19)
            condDesc = ary[18];
        if (ary.length>=20)
            condType = ary[19]; // m_item1
        if (ary.length>=21) {
            expireHour = StrUtil.toDouble(ary[20], 0); // m_item2
        }
        if (ary.length>=22) {
            expireAction = ary[21]; // m_item3;
        }
        return true;
    }

    public boolean create() {
        this.id = (int) SequenceManager.nextID(SequenceManager.OA_WORKFLOW_LINK);
        Conn conn = new Conn(connname);
        try {
            // 更新文件内容
            // (id, flow_id, action_form, action_to,speedup_date,isSpeedup) values (?,?,?,?,?,?)";
            PreparedStatement pstmt = conn.prepareStatement(INSERT);
            pstmt.setInt(1, id);
            pstmt.setInt(2, flowId);
            pstmt.setString(3, from);
            pstmt.setString(4, to);
            pstmt.setDate(5, new java.sql.Date(speedupDate.getTimeInMillis()));
            pstmt.setInt(6, isSpeedup);
            pstmt.setString(7, title);
            pstmt.setInt(8, type);
            pstmt.setString(9, condDesc);
            pstmt.setString(10, condType);
            pstmt.setDouble(11, expireHour);
            pstmt.setString(12, expireAction);
            int r = conn.executePreUpdate();
            if (r==1)
                return true;
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("create:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return false;
    }

    public boolean save() {
        Conn conn = new Conn(connname);
        try {
            // 更新文件内容
            PreparedStatement pstmt = conn.prepareStatement(SAVE);
            pstmt.setString(1, from);
            pstmt.setString(2, to);
            pstmt.setInt(3, isSpeedup);
            if (speedupDate==null)
                pstmt.setTimestamp(4, null);
            else
                pstmt.setTimestamp(4, new Timestamp(speedupDate.getTimeInMillis()));
            pstmt.setString(5, title);
            pstmt.setInt(6, type);
            pstmt.setString(7, condDesc);
            pstmt.setString(8, condType);
            pstmt.setDouble(9, expireHour);
            pstmt.setString(10, expireAction);
            pstmt.setInt(11, id);
            int r = conn.executePreUpdate();
            if (r==1)
                return true;
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("save:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return false;
    }

    public String tran(String str) {
        str = str.replaceAll("\\\\quot", "\"");
        str = str.replaceAll("\\\\colon", ":");
        str = str.replaceAll("\\\\semicolon", ";");
        str = str.replaceAll("\\\\comma", ",");
        str = str.replaceAll("\\\\newline","\r\n");
        return str;
    }

    public WorkflowLinkDb getWorkflowLinkDb(int id) {
        WorkflowLinkCacheMgr wacm = new WorkflowLinkCacheMgr();
        return wacm.getWorkflowLinkDb(id);
    }

    public boolean del() {
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        try {
            pstmt = conn.prepareStatement(DELETE);
            pstmt.setInt(1, id);
            int r = pstmt.executeUpdate();
            if (r==1) {
                // 更新缓存
                WorkflowLinkCacheMgr wlc = new WorkflowLinkCacheMgr();
                wlc.refreshDel(id);
                return true;
            }
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error(e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return false;
    }

    private int id;
    private int flowId;
    private int isSpeedup;
    private String to;
    private String from;

    public void loadFromDb() {
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(LOAD);
            pstmt.setInt(1, id);
            rs = conn.executePreQuery();
            if (!rs.next()) {
                Logger.getLogger(getClass()).error("流程连接id= " + id +
                             " 在数据库中未找到.");
            } else {
                this.flowId = rs.getInt(1);
                this.from = rs.getString(2);
                this.to = rs.getString(3);
                this.isSpeedup = rs.getInt(4);
                this.speedupDate.setTime(rs.getDate(5));
                this.title = StrUtil.getNullString(rs.getString(6));
                this.type = rs.getInt(7);
                this.condDesc = StrUtil.getNullString(rs.getString(8));
                condType = StrUtil.getNullString(rs.getString(9));
                expireHour = rs.getDouble(10);
                expireAction = StrUtil.getNullStr(rs.getString(11));
                loaded = true;
            }
        } catch (SQLException e) {
            Logger.getLogger(getClass()).error("loadFromDb:" + e.getMessage());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
    }

    /**
     * 取得到期时间
     * @param wld WorkflowLinkDb
     * @return Date
     */
    public java.util.Date calulateExpireDate() {
        // 加上对于休息日的处理，如果在指定处理时间（小时）范围内，有工作日，则顺延
        // 如果关联
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        LogUtil.getLog(getClass()).info("flowExpireRelateOACalendar id=" + cfg.get("flowExpireRelateOACalendar"));

        if (cfg.get("flowExpireRelateOACalendar").equals("true")) {
            String flowExpireUnit = cfg.get("flowExpireUnit");
            LogUtil.getLog(getClass()).info("flowExpireUnit id=" + flowExpireUnit);

            if (flowExpireUnit.equals("day")) {
                // 当天不计入超时时间
                // 遍历指定的当天其后的expire天，如果是休息日，则不计入，往后顺延
                int expireDay = (int)getExpireHour();

                return OACalendarDb.addWorkDay(expireDay);
            }
            else {
                return OACalendarDb.addWorkHour(expireHour);
            }
        }
        else {
            String flowExpireUnit = cfg.get("flowExpireUnit");
            if (flowExpireUnit.equals("day")) {
                return DateUtil.addDate(new java.
                        util.
                        Date(),
                        (int)getExpireHour());
            }
            else {
                return OACalendarDb.addHour(new java.
                        util.
                        Date(),
                        getExpireHour());
            }
        }
    }

    private int type = TYPE_TOWARD;
    private String condDesc;
    private String condType;
    private boolean loaded = false;
    private double expireHour = 0;
}
