/*
 * Copyright 2016 Nebula Bay.
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
package com.tascape.qa.th.test;

import com.alee.laf.WebLookAndFeel;
import com.alee.laf.progressbar.WebProgressBar;
import com.alee.utils.swing.ComponentUpdater;
import com.tascape.qa.th.comm.WebServiceCommunication;
import com.tascape.qa.th.ui.SmartScroller;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
public interface WebServiceTest {

    int TIMEOUT_MINUTES = 30;

    default void testManually(WebServiceCommunication wsc) throws Exception {
        testManually(wsc, TIMEOUT_MINUTES);
    }

    /**
     * The method starts a GUI to let a tester send requests to web service manually.
     * Please make sure to set test case timeout long enough for manual test cases.
     *
     * @param wsc            the WebServiceCommunication instance used in test case
     * @param timeoutMinutes timeout in minutes to fail the manual steps
     *
     * @throws Exception if case of error
     */
    default void testManually(WebServiceCommunication wsc, int timeoutMinutes) throws Exception {
        final Logger LOG = LoggerFactory.getLogger(WebServiceTest.class);

        LOG.info("Start UI to test manually");
        String info = wsc.getHttpHost().toString();
        long end = System.currentTimeMillis() + timeoutMinutes * 60000L;

        AtomicBoolean visible = new AtomicBoolean(true);
        AtomicBoolean pass = new AtomicBoolean(false);
        String tName = Thread.currentThread().getName() + "m";
        SwingUtilities.invokeLater(() -> {
            WebLookAndFeel.install();
            JDialog jd = new JDialog((JFrame) null, "Manual Web Service Interaction");
            jd.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            JPanel jpContent = new JPanel(new BorderLayout());
            jd.setContentPane(jpContent);
            jpContent.setPreferredSize(new Dimension(1088, 828));
            jpContent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JTextField jtfEndpoint = new JTextField();
            JTextField jtfParameters = new JTextField();

            JTextArea jtaContent = new JTextArea();
            Font font = jtaContent.getFont();
            jtaContent.setFont(new Font("Courier New", font.getStyle(), font.getSize()));

            JTextArea jtaResponse = new JTextArea();
            font = jtaResponse.getFont();
            jtaResponse.setFont(new Font("Courier New", font.getStyle(), font.getSize()));

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
                jpInfo.add(jb);
                jpInfo.add(jb, BorderLayout.LINE_END);
                jb.addActionListener(event -> {
                    pass.set(false);
                    jd.dispose();
                    visible.set(false);
                });
            }

            jpInfo.add(new JLabel(info, SwingConstants.CENTER), BorderLayout.CENTER);

            JPanel jpProgress = new JPanel(new BorderLayout());
            jpInfo.add(jpProgress, BorderLayout.PAGE_END);

            JPanel jpRequest = new JPanel(new BorderLayout());
            {
                JPanel jpHttp = new JPanel();
                jpHttp.setLayout(new BoxLayout(jpHttp, BoxLayout.PAGE_AXIS));
                jpRequest.add(jpHttp, BorderLayout.PAGE_START);

                JPanel jpHttp1 = new JPanel();
                jpHttp1.setLayout(new BoxLayout(jpHttp1, BoxLayout.LINE_AXIS));
                jpHttp.add(jpHttp1);

                String[] methods = new String[]{"GET", "GET JSONObject", "GET JSONArray", "POST", "POST JSONObject",
                    "PUT", "PUT JSONObject", "DELETE", "HEAD"};
                JComboBox<String> jcbMethods = new JComboBox<>(methods);
                jpHttp1.add(jcbMethods);
                jpHttp1.add(Box.createHorizontalStrut(18));
                jpHttp1.add(new JLabel("endpoint: "));
                jpHttp1.add(Box.createHorizontalStrut(8));
                jpHttp1.add(jtfEndpoint);

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
                            String pm = jtfParameters.getText();
                            String ct = jtaContent.getText();
                            String requestId = UUID.randomUUID().toString();
                            String res;
                            JSONObject json;

                            jtaResponse.setText("");
                            try {
                                switch (methods[jcbMethods.getSelectedIndex()]) {
                                    case "GET":
                                        res = wsc.get(ep, pm, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "GET JSONObject":
                                        json = wsc.getJsonObject(ep, pm, requestId);
                                        jtaResponse.setText(json.toString(2));
                                        break;
                                    case "GET JSONArray":
                                        JSONArray jarr = wsc.getJsonArray(ep, pm, requestId);
                                        jtaResponse.setText(jarr.toString(2));
                                        break;
                                    case "POST":
                                        res = wsc.post(ep, pm, ct, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "POST JSONObject":
                                        res = wsc.postJson(ep, pm, new JSONObject(ct), requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "PUT":
                                        res = wsc.put(ep, pm, ct, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "PUT JSONObject":
                                        res = wsc.putJson(ep, pm, new JSONObject(ct), requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "DELETE":
                                        res = wsc.delete(ep, pm, requestId);
                                        jtaResponse.setText(res);
                                        break;
                                    case "HEAD":
                                        for (Header h : wsc.head(ep, pm, requestId)) {
                                            jtaResponse.append(h.getName() + ": " + h.getValue() + "\n");
                                        }
                                        break;
                                }
                            } catch (IOException | JSONException ex) {
                                LOG.error(error, ex);
                            }
                            long time = wsc.getResponseTime(requestId);
                            jtaResponse.append("\n\nresponse time (ms) " + time);
                            LOG.debug("\n\n");
                        }
                    };
                    t.start();

                    try {
                        t.join();
                    } catch (InterruptedException ex) {
                        LOG.error(error, ex);
                    }
                }
                );

                jtaContent.setTabSize(4);
                JScrollPane jsp = new JScrollPane(jtaContent);
                new SmartScroller(jsp);
                jpRequest.add(jsp, BorderLayout.CENTER);
            }

            JPanel jpResponse = new JPanel(new BorderLayout());
            {
                JScrollPane jsp = new JScrollPane(jtaResponse);
                new SmartScroller(jsp);
                jpResponse.add(jsp, BorderLayout.CENTER);
            }

            JPanel jpLog = new JPanel();
            jpLog.setLayout(new BoxLayout(jpLog, BoxLayout.LINE_AXIS));
            jpResponse.add(jpLog, BorderLayout.PAGE_START);
            {
                JButton jbLogMsg = new JButton("Log Message");
                jpLog.add(jbLogMsg);
                JTextField jtMsg = new JTextField(10);
                jpLog.add(jtMsg);
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
                        LOG.error("Cannot take screenshot", ex);
                    }
                    jtMsg.requestFocus();
                });
            }

            WebProgressBar jpb = new WebProgressBar(0, timeoutMinutes * 60);
            jpb.setIndeterminate(true);
            jpb.setIndeterminate(false);
            jpb.setStringPainted(true);
            jpb.setString("");
            jpProgress.add(jpb);

            ComponentUpdater.install(jpb, 1000, (ActionEvent e) -> {
                int second = (int) (end - System.currentTimeMillis()) / 1000;
                jpb.setValue(second);
                jpb.setString(second + " seconds left");
                if (second < 60) {
                    jpb.setForeground(Color.red);
                } else if (second < 300) {
                    jpb.setForeground(Color.blue);
                } else {
                    jpb.setForeground(Color.green.darker());
                }
            });

            JSplitPane jSplitPaneReqRes = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jpRequest, jpResponse);
            jSplitPaneReqRes.setResizeWeight(0.5);
            jSplitPaneReqRes.setBorder(BorderFactory.createEtchedBorder());

            JPanel jpHistory = new JPanel(new BorderLayout());
            {
                jpHistory.setBorder(BorderFactory.createEtchedBorder());
                JList<String> jlHistory = new JList<>(new String[]{"req1", "re12"});
                jpHistory.add(jlHistory, BorderLayout.CENTER);

                JPanel jpButtons = new JPanel();
                jpButtons.setLayout(new BoxLayout(jpButtons, BoxLayout.LINE_AXIS));
                jpHistory.add(jpButtons, BorderLayout.PAGE_END);
                JButton jbLoad = new JButton("Load History");
                jpButtons.add(jbLoad);
                jpButtons.add(Box.createHorizontalGlue());
                JButton jbSave = new JButton("Save History");
                jpButtons.add(jbSave);
            }

            JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jpHistory, jSplitPaneReqRes);
            jSplitPane.setResizeWeight(0.2);
            jSplitPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

            jpContent.add(jSplitPane, BorderLayout.CENTER);
            jd.pack();
            jd.setVisible(true);
            jd.setAlwaysOnTop(true);
            jd.setLocationRelativeTo(null);
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
}
