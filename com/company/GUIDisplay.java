package com.company;

import javax.swing.*;
import java.awt.*;

public class GUIDisplay {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GUIDisplay::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame f = new JFrame();
        HeatPropagator hp = new HeatPropagator();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        GraphicsEnvironment graphics = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = graphics.getDefaultScreenDevice();
        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
        f.add(hp);
        f.setUndecorated(true);
        device.setFullScreenWindow(f);
        //new Timer(10, e -> hp.repaint()).start();
        f.setVisible(true);
        f.setSize(f.getWidth(), f.getHeight());
        int matrixWidth = f.getWidth() / 10;
        int matrixHeight = f.getHeight() / 10;
        float redhottemp = 500;
        hp.begin(redhottemp, matrixWidth, matrixHeight);
    }
}