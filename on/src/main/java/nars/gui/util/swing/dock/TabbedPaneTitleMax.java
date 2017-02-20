/*
 * Copyright 2011 Mark McKay
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * TabbedPaneTitle.java
 *
 * Created on Jan 12, 2011, 8:06:49 AM
 */
package nars.gui.util.swing.dock;

import nars.gui.util.swing.AwesomeButton;

import static javax.swing.BorderFactory.createEmptyBorder;


/**
 *
 * @author kitfox
 */
@Deprecated public class TabbedPaneTitleMax extends javax.swing.JPanel {

    final DockingRegionMaximized tabPanel;
    final DockingContent content;


    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JButton bn_close;
    protected javax.swing.JButton bn_float;
    //protected javax.swing.JButton bn_minimize;
    protected javax.swing.JLabel label_title;

    // End of variables declaration//GEN-END:variables
    
    /** called from subclass */
    public TabbedPaneTitleMax(DockingContent content) {
        this.tabPanel = null;
        this.content = content;
        initButtons();
    }
    
    /**
     * Creates new form TabbedPaneTitle
     */
    public TabbedPaneTitleMax(DockingRegionMaximized tabPanel, DockingContent content) {
        this.tabPanel = tabPanel;
        this.content = content;

        initButtons();
        initComponents();
    }

    protected void initButtons() {
        bn_float = new AwesomeButton('\uf0de');
        bn_float.setToolTipText("Pop Out to Floating Window");
        
        //bn_minimize = new NARSwing.AwesomeButton('\uf0dd');
        bn_close = new AwesomeButton('\uf00d');
        label_title = new javax.swing.JLabel();        
        label_title.setText(content.getTitle());
        label_title.setIcon(content.getIcon());
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    @SuppressWarnings("unchecked")
    private void initComponents() {
        
        
        setOpaque(false);
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        label_title.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        //label_title.setText("jLabel1");
        label_title.setBorder(createEmptyBorder(0, 0, 0, 0));
        label_title.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                label_titleMouseClicked(evt);
            }
        });
        label_title.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                label_titleMouseDragged(evt);
            }
        });
        add(label_title);
        //bn_float.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/float.png"))); // NOI18N
        bn_float.setBorderPainted(false);
        bn_float.setContentAreaFilled(false);
        //bn_float.setMargin(new java.awt.Insets(0, 0, 0, 0));
        bn_float.addActionListener(evt -> bn_floatActionPerformed(evt));
        add(bn_float);
        
//        //bn_minimize.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/minimize.png"))); // NOI18N
//        bn_minimize.setBorderPainted(false);
//        bn_minimize.setContentAreaFilled(false);
//        bn_minimize.setMargin(new java.awt.Insets(0, 0, 0, 0));
//        bn_minimize.addActionListener(new java.awt.event.ActionListener() {
//            @Override
//            public void actionPerformed(java.awt.event.ActionEvent evt) {
//                bn_minimizeActionPerformed(evt);
//            }
//        });
//        add(bn_minimize);
        
//bn_close.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/close.png"))); // NOI18N
        bn_close.setBorderPainted(false);
        bn_close.setContentAreaFilled(false);
        //bn_close.setMargin(new java.awt.Insets(0, 0, 0, 0));
        bn_close.addActionListener(evt -> bn_closeActionPerformed(evt));
        add(bn_close);
    } // </editor-fold>//GEN-END:initComponents

    private void bn_floatActionPerformed(java.awt.event.ActionEvent evt) //GEN-FIRST:event_bn_floatActionPerformed
    {
        //GEN-HEADEREND:event_bn_floatActionPerformed
        tabPanel.floatFromMaximize(content);
    } //GEN-LAST:event_bn_floatActionPerformed

    private void bn_minimizeActionPerformed(java.awt.event.ActionEvent evt) //GEN-FIRST:event_bn_minimizeActionPerformed
    {
        //GEN-HEADEREND:event_bn_minimizeActionPerformed
        tabPanel.restoreFromMaximize(content);
    } //GEN-LAST:event_bn_minimizeActionPerformed

    private void bn_closeActionPerformed(java.awt.event.ActionEvent evt) //GEN-FIRST:event_bn_closeActionPerformed
    {
        //GEN-HEADEREND:event_bn_closeActionPerformed
        tabPanel.closeFromMaximize(content);
    } //GEN-LAST:event_bn_closeActionPerformed

    private static void label_titleMouseDragged(java.awt.event.MouseEvent evt) //GEN-FIRST:event_label_titleMouseDragged
    {
        //GEN-HEADEREND:event_label_titleMouseDragged
        System.err.println("Dragging");
    } //GEN-LAST:event_label_titleMouseDragged

    private void label_titleMouseClicked(java.awt.event.MouseEvent evt) //GEN-FIRST:event_label_titleMouseClicked
    {
        //GEN-HEADEREND:event_label_titleMouseClicked
//        tabPanel.selectTab(content);
    } //GEN-LAST:event_label_titleMouseClicked

}
