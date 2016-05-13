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
package com.tascape.qa.th.tools;

import com.alee.laf.WebLookAndFeel;
import com.tascape.qa.th.SystemConfiguration;
import com.tascape.qa.th.comm.WebServiceCommunication;
import com.tascape.qa.th.test.WebServiceTest;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public class WebServiceDebugger implements WebServiceTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(WebServiceDebugger.class);

    private WebServiceCommunication wsc;

    private String host = "localhost";

    private final JTextField jtfHost = new JTextField();

    private int port = 8443;

    private final JTextField jtfPort = new JTextField();

    private String user = "";

    private final JTextField jtfUser = new JTextField();

    private String pass = "";

    private final JPasswordField jtfPass = new JPasswordField();

    private String clientCertFile = "";

    private final JTextField jtfClientCertFile = new JTextField();

    private String clientCertPass = "";

    private final JPasswordField jtfClientCertPass = new JPasswordField();

    private int debugMinutes = 30;

    private final JSpinner jsDebugMinutes = new JSpinner(new SpinnerNumberModel(30, 15, 180, 15));

    private JDialog jd;

    private final JPanel jpParameters = new JPanel();

    private void start() throws Exception {
        SwingUtilities.invokeLater(() -> {
            WebLookAndFeel.install();
            jd = new JDialog((Frame) null, "Connect to Web Service");
            jd.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            JPanel jpContent = new JPanel(new BorderLayout());
            jd.setContentPane(jpContent);
            jpContent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            jpParameters.setLayout(new BoxLayout(jpParameters, BoxLayout.PAGE_AXIS));
            jpContent.add(jpParameters, BorderLayout.CENTER);

            jtfHost.setText(host);
            jtfHost.setToolTipText("-D" + WebServiceCommunication.SYSPROP_HOST);
            addParameter("Host", jtfHost);
            jtfPort.setText(port + "");
            jtfPort.setToolTipText("-D" + WebServiceCommunication.SYSPROP_PORT);
            addParameter("Port", jtfPort);
            jtfUser.setText(user);
            jtfUser.setToolTipText("-D" + WebServiceCommunication.SYSPROP_USER);
            addParameter("User", jtfUser);
            jtfPass.setText(pass);
            jtfPass.setToolTipText("-D" + WebServiceCommunication.SYSPROP_PASS);
            addParameter("Pass", jtfPass);
            jtfClientCertFile.setText(clientCertFile);
            jtfClientCertFile.setToolTipText("-D" + WebServiceCommunication.SYSPROP_CLIENT_CERT);
            addParameter("Client Cert File", jtfClientCertFile);
            jtfClientCertPass.setText(clientCertPass);
            jtfClientCertPass.setToolTipText("-D" + WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS);
            addParameter("Client Cert Pass", jtfClientCertPass);
            jsDebugMinutes.getEditor().setEnabled(false);
            addParameter("Interaction time (minute)", jsDebugMinutes);
            addParameter("", Box.createRigidArea(new Dimension(588, 2)));

            JPanel jpInfo = new JPanel();
            jpContent.add(jpInfo, BorderLayout.PAGE_END);
            jpInfo.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            jpInfo.setLayout(new BoxLayout(jpInfo, BoxLayout.LINE_AXIS));

            JButton jbLoad = new JButton("Load");
            jbLoad.setToolTipText("load properties from a file");
            jpInfo.add(jbLoad);
            jbLoad.addActionListener(event -> {
                JFileChooser chooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Properties file", "properties");
                chooser.setFileFilter(filter);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int returnVal = chooser.showOpenDialog(jd);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    LOG.debug("load properties from {}", file);
                    Properties properties = new Properties();
                    try (InputStream is = FileUtils.openInputStream(file)) {
                        properties.load(is);
                    } catch (IOException ex) {
                        LOG.error("cannot load properties", ex);
                    }
                    String value = properties.getProperty(WebServiceCommunication.SYSPROP_HOST);
                    if (StringUtils.isNotBlank(value)) {
                        jtfHost.setText(value);
                    }
                    value = properties.getProperty(WebServiceCommunication.SYSPROP_PORT);
                    if (StringUtils.isNotBlank(value)) {
                        jtfPort.setText(value);
                    }
                    value = properties.getProperty(WebServiceCommunication.SYSPROP_USER);
                    if (StringUtils.isNotBlank(value)) {
                        jtfUser.setText(value);
                    }
                    value = properties.getProperty(WebServiceCommunication.SYSPROP_PASS);
                    if (StringUtils.isNotBlank(value)) {
                        jtfPass.setText(value);
                    }
                    value = properties.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT);
                    if (StringUtils.isNotBlank(value)) {
                        jtfClientCertFile.setText(value);
                    }
                    value = properties.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS);
                    if (StringUtils.isNotBlank(value)) {
                        jtfClientCertPass.setText(value);
                    }
                }
            });
            jpInfo.add(Box.createHorizontalStrut(5));

            JButton jbSave = new JButton("Save");
            jbSave.setToolTipText("save current properties into a file");
            jpInfo.add(jbSave);
            jbSave.addActionListener(event -> {
                JFileChooser chooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Properties file", "properties");
                chooser.setFileFilter(filter);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int returnVal = chooser.showSaveDialog(jd);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    LOG.debug("load properties from {}", file);
                    Properties properties = new Properties();
                    properties.setProperty(WebServiceCommunication.SYSPROP_HOST, jtfHost.getText());
                    properties.setProperty(WebServiceCommunication.SYSPROP_PORT, jtfPort.getText());
                    properties.setProperty(WebServiceCommunication.SYSPROP_USER, jtfUser.getText());
                    properties.setProperty(WebServiceCommunication.SYSPROP_PASS, new String(jtfPass.getPassword()));
                    properties.setProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT, jtfClientCertFile.getText());
                    properties.setProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS,
                        new String(jtfClientCertPass.getPassword()));

                    try (OutputStream os = FileUtils.openOutputStream(file)) {
                        properties.store(os, "");
                    } catch (IOException ex) {
                        LOG.error("cannot save properties", ex);
                    }
                }
            });
            jpInfo.add(Box.createHorizontalGlue());

            JButton jbConnect = new JButton("Connect");
            jbConnect.setFont(jbConnect.getFont().deriveFont(Font.BOLD));
            jpInfo.add(jbConnect);
            jbConnect.addActionListener(event -> {
                new Thread() {
                    @Override
                    public void run() {
                        connect();
                    }
                }.start();
            });

            jd.pack();
            jd.setResizable(false);
            jd.setAlwaysOnTop(true);
            jd.setLocationRelativeTo(null);
            jd.setVisible(true);
        });
    }

    private void connect() {
        try {
            wsc = new WebServiceCommunication(jtfHost.getText(), Integer.parseInt(jtfPort.getText()));
            if (StringUtils.isNotBlank(jtfUser.getText())) {
                wsc.setBasicUsernamePassword(jtfUser.getText(), new String(jtfPass.getPassword()));
            }
            if (StringUtils.isNotBlank(jtfClientCertFile.getText())) {
                wsc.setClientCertificate(jtfClientCertFile.getText(), new String(jtfClientCertPass.getPassword()));
            }
            wsc.connect();
        } catch (Throwable ex) {
            LOG.error("Error", ex);
            jd.setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(jd, "Cannot connect to service");
            return;
        }

        debugMinutes = (int) jsDebugMinutes.getValue();
        jd.dispose();
        try {
            this.testManually(wsc, debugMinutes);
        } catch (Throwable ex) {
            LOG.error("Error", ex);
            System.exit(1);
        }
        System.exit(0);
    }

    private void addParameter(String label, Component component) {
        JPanel jp = new JPanel();
        jpParameters.add(jp);
        jp.setLayout(new BoxLayout(jp, BoxLayout.LINE_AXIS));
        jp.add(new JLabel(label));
        jp.add(Box.createRigidArea(new Dimension(38, 2)));
        jp.add(component);
    }

    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    public static void main(String[] args) {
        SystemConfiguration conf = SystemConfiguration.getInstance();

        WebServiceDebugger debugger = new WebServiceDebugger();
        debugger.host = conf.getProperty(WebServiceCommunication.SYSPROP_HOST, "localhost");
        debugger.port = conf.getIntProperty(WebServiceCommunication.SYSPROP_PORT, 8443);
        debugger.user = conf.getProperty(WebServiceCommunication.SYSPROP_USER, "");
        debugger.pass = conf.getProperty(WebServiceCommunication.SYSPROP_PASS, "");
        debugger.clientCertFile = conf.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT, "");
        debugger.clientCertPass = conf.getProperty(WebServiceCommunication.SYSPROP_CLIENT_CERT_PASS, "");

        try {
            debugger.start();
        } catch (Throwable ex) {
            LOG.error("", ex);
            System.exit(1);
        }
    }
}
