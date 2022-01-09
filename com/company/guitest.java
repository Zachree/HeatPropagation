package com.company;

import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

public class guitest extends Canvas {
    // alloy dimensions
    //static final int WIDTH = 193, HEIGHT = 96;
    static int WIDTH, HEIGHT;

    public void paint(Graphics g) {
        float redhottemp = 500;
        new CCJacobi(g, redhottemp, WIDTH, HEIGHT);

        /*for(int i = 0; i < 108; i++) {
            for(int j = 0; j < 192; j++) {
                // in HSB, 0.6 results in blue and red is at 0 or 1. this formula bounds the result using x from .6 to 0,
                // and can be altered to turn red at a higher point using redhottemp.
                float hsb = ((redhottemp-x++) / redhottemp) * .6f;
                if(hsb < 0)
                    hsb = 0;
                Color c = Color.getHSBColor(hsb, 1, 1);
                g.setColor(c);
                g.fillRect(j * 10, i * 10,10, 10);
            }
        }*/
    }

    // invokeLater somewhere for starting the GUI ?
    public static void main(String[] args) {
        guitest m = new guitest();
        JFrame f = new JFrame();
        GraphicsEnvironment graphics = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = graphics.getDefaultScreenDevice();
        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
        f.setUndecorated(true);
        device.setFullScreenWindow(f);
        f.add(m);
        f.setVisible(true);
        f.setSize(f.getWidth(), f.getHeight());
        WIDTH = f.getWidth() / 10;
        HEIGHT = f.getHeight() / 10;
        //f.setSize(WIDTH*10,HEIGHT*10);
    }
}
