package com.company;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.util.concurrent.Executors;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class uh {

    protected void initUI() {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle(uh.class.getSimpleName());
        final Random rand = new Random();
        final JPanel comp = new JPanel() {
            private String value;

            @Override
            public void paintComponent(Graphics g) {
                value = "hello";
                super.paintComponent(g);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                g.setColor(new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256)));
                g.fillRect(0, 0, getWidth(), getHeight());
                if (SwingUtilities.isEventDispatchThread()) {
                    System.err.println("Painting in the EDT " + getValue());
                } else {
                    System.err.println("Not painting in EDT " + getValue());
                }
                value = null;
            }

            public String getValue() {
                return value;
            }
        };
        frame.add(comp);
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        new Timer(1, e -> comp.repaint()).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new uh().initUI();
            }
        });
    }

}