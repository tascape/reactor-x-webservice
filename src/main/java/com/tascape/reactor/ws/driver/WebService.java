/*
 * Copyright (c) 2015 - present Nebula Bay.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tascape.reactor.ws.driver;

import com.tascape.reactor.SystemConfiguration;
import com.tascape.reactor.comm.SshCommunication;
import com.tascape.reactor.ws.comm.WebServiceCommunication;
import com.tascape.reactor.driver.EntityDriver;
import com.tascape.reactor.ws.comm.WebServiceCommunication.HTTP_METHOD;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.LastModifiedFileComparator;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public abstract class WebService extends EntityDriver {
    private static final Logger LOG = LoggerFactory.getLogger(WebService.class);

    private static final File HISTORY_DIR = SystemConfiguration.HOME_PATH.resolve("ws-viewer").toFile();

    protected WebServiceCommunication wsc;

    protected SshCommunication ssh;

    public void setWebServiceCommunication(WebServiceCommunication wsc) {
        this.wsc = wsc;
    }

    public WebServiceCommunication getWebServiceCommunication() {
        return this.wsc;
    }

    public SshCommunication getSshCommunication() {
        return ssh;
    }

    public void setSshCommunication(SshCommunication ssh) {
        this.ssh = ssh;
    }

    public void interactManually() throws Exception {
        WebService.this.interactManually(30);
    }

    public void logIntoFile(String prefix, JSONObject json) throws IOException {
        this.saveIntoFile(prefix, "json", json.toString(2));
    }

    /**
     * The method starts a GUI to let an user send requests to web service manually.
     * Please make sure to set timeout long enough for manual interaction.
     *
     * @param timeoutMinutes timeout in minutes to fail the manual steps
     *
     * @throws Exception if case of error
     */
    public void interactManually(int timeoutMinutes) throws Exception {
        LOG.info("Start UI to interact manually");
        String info = wsc.getHttpHost().toString();
        long end = System.currentTimeMillis() + timeoutMinutes * 60000L;

        AtomicBoolean visible = new AtomicBoolean(true);
        AtomicBoolean pass = new AtomicBoolean(false);
        String tName = Thread.currentThread().getName() + "m";
        SwingUtilities.invokeLater(() -> {
            JDialog jd = new JDialog((Frame) null, "Manual Web Service Interaction");
            jd.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            JPanel jpContent = new JPanel(new BorderLayout());
            jd.setContentPane(jpContent);
            jpContent.setPreferredSize(new Dimension(1088, 828));
            jpContent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JComboBox<HTTP_METHOD> jcbMethods = new JComboBox<>(HTTP_METHOD.values());
            JTextField jtfEndpoint = new JTextField();
            JTextField jtfParameters = new JTextField();

            JTextArea jtaRequest = new JTextArea();
            Font font = jtaRequest.getFont();
            jtaRequest.setFont(new Font("Courier New", font.getStyle(), font.getSize()));

            JTextArea jtaResponse = new JTextArea();
            font = jtaResponse.getFont();
            jtaResponse.setFont(new Font("Courier New", font.getStyle(), font.getSize()));

            DefaultListModel<JSONObject> historyModel = this.loadHistoryModel();
            JList<JSONObject> jlHistory = new JList<>(historyModel);
            jlHistory.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                    setBackground(list.getBackground());
                    JSONObject json = (JSONObject) value;
                    setText(json.getString("endpoint"));
                    setToolTipText(json.getString("endpoint"));
                    if (isSelected) {
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                    return this;
                }
            });
            jlHistory.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            jlHistory.clearSelection();
            jlHistory.getSelectionModel().addListSelectionListener(event -> {
                ListSelectionModel lsm = (ListSelectionModel) event.getSource();
                if (event.getValueIsAdjusting()) {
                    return;
                }
                JSONObject json = historyModel.get(lsm.getMinSelectionIndex());
                jcbMethods.setSelectedItem(HTTP_METHOD.valueOf(json.getString("method")));
                jtfEndpoint.setText(json.getString("endpoint"));
                jtfParameters.setText(json.getString("parameters"));
                jtaRequest.setText(json.getString("req-body"));
                jtaResponse.setText(json.getString("res-body"));
            });

            JPanel jpInfo = new JPanel();
            jpContent.add(jpInfo, BorderLayout.PAGE_START);
            jpInfo.setLayout(new BorderLayout());
            {
                JButton jb = new JButton("PASS");
                jb.setForeground(Color.green.darker());
                jb.setFont(jb.getFont().deriveFont(Font.BOLD));
                jpInfo.add(jb, BorderLayout.LINE_START);
                jb.addActionListener(event -> {
                    pass.set(true);
                    jd.dispose();
                    visible.set(false);
                });
            }
            {
                JButton jb = new JButton("FAIL");
                jb.setForeground(Color.red);
                jb.setFont(jb.getFont().deriveFont(Font.BOLD));
                jpInfo.add(jb, BorderLayout.LINE_END);
                jb.addActionListener(event -> {
                    pass.set(false);
                    jd.dispose();
                    visible.set(false);
                });
            }
            JLabel jlTimeout = new JLabel("1200 seconds left", SwingConstants.CENTER);
            jpInfo.add(jlTimeout, BorderLayout.CENTER);
            new SwingWorker<Long, Long>() {
                @Override
                protected Long doInBackground() throws Exception {
                    while (System.currentTimeMillis() < end) {
                        Thread.sleep(1000);
                        long left = (end - System.currentTimeMillis()) / 1000;
                        this.publish(left);
                    }
                    return 0L;
                }

                @Override
                protected void process(List<Long> chunks) {
                    Long l = chunks.get(chunks.size() - 1);
                    jlTimeout.setText(l + " seconds left");
                    if (l < 850) {
                        jlTimeout.setForeground(Color.red);
                    }
                }
            }.execute();

            JPanel jpRequest = new JPanel(new BorderLayout());
            {
                JPanel jpHttp = new JPanel();
                jpHttp.setLayout(new BoxLayout(jpHttp, BoxLayout.PAGE_AXIS));
                jpRequest.add(jpHttp, BorderLayout.PAGE_START);

                JPanel jpHttp1 = new JPanel();
                jpHttp1.setLayout(new BoxLayout(jpHttp1, BoxLayout.LINE_AXIS));
                jpHttp.add(jpHttp1);

                jpHttp1.add(jcbMethods);
                jpHttp1.add(Box.createHorizontalStrut(18));
                jpHttp1.add(new JLabel(info, SwingConstants.CENTER), BorderLayout.CENTER);
                jpHttp1.add(Box.createHorizontalStrut(8));
                jpHttp1.add(new JLabel("endpoint: "));
                jpHttp1.add(Box.createHorizontalStrut(5));
                jpHttp1.add(jtfEndpoint);
                jpHttp1.add(Box.createHorizontalStrut(8));
                JButton jbHeaders = new JButton("Headers");
                jpHttp1.add(jbHeaders);
                jbHeaders.addActionListener((ActionEvent event) -> {
                    Thread t = new Thread(tName) {
                        @Override
                        public void run() {
                            JPanel jpHeaders = new JPanel(new BorderLayout());
                            jpHeaders.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                            DefaultTableModel tm = new DefaultTableModel(1, 2);
                            tm.setColumnIdentifiers(new String[]{"Name", "Value"});
                            wsc.getHeaders().entrySet().forEach(es -> {
                                tm.insertRow(0, new String[]{es.getKey(), es.getValue()});
                            });
                            tm.addRow(new String[]{"", ""});

                            JTable jt = new JTable(tm);
                            jpHeaders.add(new JScrollPane(jt), BorderLayout.CENTER);

                            JPanel jp = new JPanel();
                            jpHeaders.add(jp, BorderLayout.PAGE_END);
                            jp.setLayout(new BoxLayout(jp, BoxLayout.LINE_AXIS));
                            JButton apply = new JButton("Apply");
                            jp.add(Box.createHorizontalGlue());
                            jp.add(apply);

                            JDialog jd0 = new JDialog(jd, "HTTP Headers");
                            jd0.setContentPane(jpHeaders);
                            jd0.setModal(true);
                            jd0.pack();
                            jd0.setLocationRelativeTo(jd);
                            jd0.setVisible(true);

                            apply.addActionListener((ActionEvent e) -> {
                                for (int i = 0, j = jt.getModel().getRowCount(); i < j; i++) {
                                    wsc.getHeaders().put(jt.getValueAt(i, 0) + "", jt.getValueAt(i, 1) + "");
                                }
                                jd0.dispose();
                            });
                        }
                    };
                    t.start();
                });

                JPanel jpHttp2 = new JPanel();
                jpHttp2.setLayout(new BoxLayout(jpHttp2, BoxLayout.LINE_AXIS));
                jpHttp.add(jpHttp2);

                jpHttp2.add(new JLabel("parameters: "));
                jpHttp2.add(Box.createHorizontalStrut(8));
                jpHttp2.add(jtfParameters);
                jpHttp.add(Box.createHorizontalStrut(18));
                JButton jbSend = new JButton("Send Request");
                jpHttp2.add(jbSend);

                String error = "Cannot send request";
                jbSend.addActionListener((ActionEvent event) -> {
                    Thread t = new Thread(tName) {
                        @Override
                        public void run() {
                            LOG.debug("\n\n");
                            String ep = jtfEndpoint.getText();
                            if (StringUtils.isBlank(ep)) {
                                return;
                            }

                            String pm = jtfParameters.getText();
                            String ct = jtaRequest.getText();
                            String requestId = UUID.randomUUID().toString();
                            HTTP_METHOD method = (HTTP_METHOD) jcbMethods.getSelectedItem();
                            String res;
                            JSONObject json;

                            jtaResponse.setText("");
                            try {
                                switch (method) {
                                    case GET:
                                        res = wsc.get(ep, pm, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case GET_JSONObject:
                                        json = wsc.getJsonObject(ep, pm, requestId);
                                        jtaResponse.setText(json.toString(2));
                                        break;
                                    case GET_JSONArray:
                                        JSONArray jarr = wsc.getJsonArray(ep, pm, requestId);
                                        jtaResponse.setText(jarr.toString(2));
                                        break;
                                    case POST:
                                        res = wsc.post(ep, pm, ct, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case POST_JSONObject:
                                        res = wsc.postJson(ep, pm, new JSONObject(ct), requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case PUT:
                                        res = wsc.put(ep, pm, ct, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case PUT_JSONObject:
                                        res = wsc.putJson(ep, pm, new JSONObject(ct), requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case DELETE:
                                        if (StringUtils.isBlank(ct)) {
                                            res = wsc.delete(ep, pm, requestId);
                                        } else {
                                            res = wsc.delete(ep, pm, ct, requestId);
                                        }
                                        jtaResponse.setText(res);
                                        break;
                                    case DELETE_JSONObject:
                                        res = wsc.deleteJson(ep, pm, new JSONObject(ct), requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case HEAD:
                                        for (Header h : wsc.head(ep, pm, requestId)) {
                                            jtaResponse.append(h.getName() + ": " + h.getValue() + "\n");
                                        }
                                        break;
                                }
                            } catch (IOException | JSONException ex) {
                                LOG.error(error, ex);
                                StringWriter sw = new StringWriter();
                                ex.printStackTrace(new PrintWriter(sw));
                                jtaResponse.append(sw.toString());
                                jtaResponse.append("\n");
                            }
                            long time = wsc.getResponseTime(requestId);
                            jtaResponse.append("\n\nresponse time (ms) " + time);
                            LOG.debug("\n\n");

                            JSONObject j = new JSONObject()
                                .put("method", method.name())
                                .put("endpoint", ep)
                                .put("parameters", pm)
                                .put("req-body", ct)
                                .put("res-body", jtaResponse.getText());
                            historyModel.insertElementAt(j, 0);
                            jlHistory.setSelectedIndex(0);
                            try {
                                FileUtils.writeStringToFile(new File(HISTORY_DIR, "ws-" + System.currentTimeMillis()
                                    + ".json"), j.toString(2), Charset.defaultCharset());
                            } catch (IOException ex) {
                                LOG.warn("Cannot save history {}", ex.getMessage());
                            }
                        }
                    };
                    t.start();

                    try {
                        t.join();
                    } catch (InterruptedException ex) {
                        LOG.error(error, ex);
                    }
                });

                jtaRequest.setTabSize(4);
                JScrollPane jsp = new JScrollPane(jtaRequest);
                jsp.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5),
                    UIManager.getBorder("TextField.border")));
                jpRequest.add(new JLabel("<html>request<br>body: "), BorderLayout.LINE_START);
                jpRequest.add(jsp, BorderLayout.CENTER);
            }

            JPanel jpResponse = new JPanel(new BorderLayout());
            {
                JScrollPane jsp = new JScrollPane(jtaResponse);
                jsp.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5),
                    UIManager.getBorder("TextField.border")));
                jpResponse.add(jsp, BorderLayout.CENTER);
                jpResponse.add(new JLabel("response: "), BorderLayout.LINE_START);
            }

            JPanel jpLog = new JPanel();
            jpLog.setLayout(new BoxLayout(jpLog, BoxLayout.LINE_AXIS));
            jpResponse.add(jpLog, BorderLayout.PAGE_END);
            {
                JTextField jtMsg = new JTextField(10);
                jpLog.add(jtMsg);
                JButton jbLogMsg = new JButton("Log Message");
                jpLog.add(jbLogMsg);
                jtMsg.addFocusListener(new FocusListener() {
                    @Override
                    public void focusLost(final FocusEvent pE) {
                    }

                    @Override
                    public void focusGained(final FocusEvent pE) {
                        jtMsg.selectAll();
                    }
                });
                jbLogMsg.addActionListener(event -> {
                    Thread t = new Thread(tName) {
                        @Override
                        public void run() {
                            String msg = jtMsg.getText();
                            if (StringUtils.isNotBlank(msg)) {
                                LOG.info("{}", msg);
                            }
                        }
                    };
                    t.start();
                    try {
                        t.join();
                    } catch (InterruptedException ex) {
                        LOG.error("", ex);
                    }
                    jtMsg.requestFocus();
                });
            }
            JSplitPane jSplitPaneReqRes = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jpRequest, jpResponse);
            jSplitPaneReqRes.setResizeWeight(0.28);
            jSplitPaneReqRes.setBorder(BorderFactory.createEtchedBorder());

            JPanel jpHistory = new JPanel(new BorderLayout());
            {
                jpHistory.setBorder(BorderFactory.createEtchedBorder());
                jpHistory.add(new JScrollPane(jlHistory), BorderLayout.CENTER);
            }

            JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jpHistory, jSplitPaneReqRes);
            jSplitPane.setResizeWeight(0.18);
            jSplitPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

            jpContent.add(jSplitPane, BorderLayout.CENTER);
            jd.pack();
            jd.setLocationRelativeTo(null);
            jd.setVisible(true);
            jSplitPane.setDividerLocation(0.12);
        });

        while (visible.get()) {
            if (System.currentTimeMillis() > end) {
                LOG.error("Manual interaction timeout");
                break;
            }
            Thread.sleep(500);
        }

        if (pass.get()) {
            LOG.info("Manual Interaction returns PASS");
        } else {
            Assert.fail("Manual Interaction returns FAIL");
        }
    }

    private DefaultListModel<JSONObject> loadHistoryModel() {
        HISTORY_DIR.mkdirs();
        File[] files = HISTORY_DIR.listFiles();
        Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE);

        int number = Math.min(200, files.length);
        DefaultListModel<JSONObject> m = new DefaultListModel<>();
        for (int i = 0; i < number; i++) {
            try {
                m.addElement(new JSONObject(FileUtils.readFileToString(files[i], Charset.defaultCharset())));
            } catch (IOException ex) {
                LOG.warn("cannot load json {}", ex.getMessage());
            }
        }
        for (int i = number; i < files.length; i++) {
            files[i].delete();
        }
        return m;
    }
}
