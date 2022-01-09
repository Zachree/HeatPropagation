package com.company;

// Simulation of heat propgating through a metal alloy. Loosely based on
// CCJacobi.java by Doug Lea at http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/test/loops/CCJacobi.java?revision=1.7&view=markup&pathrev=MAIN

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.*;

public class HeatPropagator extends JPanel {
    private final double A_CONSTANT = 0.75, B_CONSTANT = 1.0, C_CONSTANT = 1.25,
            S_TEMP = 1000.0, T_TEMP = 400.0;
    private int width, height;
    private float redhottemp;
    private MetalBar paintMe;
    SwingWorker<Void, MetalBar> workerThread;

    //    final int DEFAULT_GRANULARITY = 4096;
    //    final int DEFAULT_GRANULARITY = 256;
    int DEFAULT_GRANULARITY;

    /**
     * The maximum number of matrix cells
     * at which to stop recursing down and instead directly update.
     */

    private static class Region {
        //final double A_RATIO = 0.3, B_RATIO = 0.37, C_RATIO = 0.33;
        //final double A_RATIO = 0.33, B_RATIO = 0.37, C_RATIO = 0.30;
        final double A_RATIO = 0.33, B_RATIO = 0.33, C_RATIO = 0.34;
        //final double A_RATIO = 0.37, B_RATIO = 0.33, C_RATIO = 0.30;
        double temp;
        boolean isHeatsource = false;
        public Region(double temp) {
            this.temp = temp;
        }
        public Region(double temp, boolean isHeatsource) {
            this.temp = temp;
            this.isHeatsource = isHeatsource;
        }
    }

    private static class MetalBar {
        Region[][] floor;
        public MetalBar(Region[][] floor) {
            this.floor = floor;
        }
    }

    HeatPropagator() {
        // initialize
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(paintMe != null) {
            Region[][] localRef = paintMe.floor;
            for(int i = 0; i < width; i++) {
                for(int j = 0; j < height; j++) {
                    paintCell(g, (float)localRef[i][j].temp, i, j);
                }
            }
        }
    }

    private void paintCell(Graphics g, float regionTemp, int x, int y) {
        // in HSB, 0.6 results in blue while red is at 0 and 1. this formula bounds the result (using the temperature
        // of the region) to the range of .6 to 0. The temperature at which it turns red can be changed by changing redhottemp.
        float hue = ((redhottemp-regionTemp) / redhottemp) * .7f;

        if(hue < 0) // 0 = red
            hue = 0;
        Color c = Color.getHSBColor(hue, .8f, 1);
        g.setColor(c);
        g.fillRect(x * 10, y * 10,10, 10);
    }

    public void begin(float redhottemp, int width, int height) {
        this.redhottemp = redhottemp;
        this.width = width;
        this.height = height;
        workerThread = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                heatPropagationSim();
                return null;
            }

            @Override
            protected void process(List<MetalBar> chunks) {
                for(int i = 0; i < chunks.size()-1; i++) {
                    paintMe = chunks.get(i);
                    repaint();
                }
            }

            private void heatPropagationSim() {
                int cores = Runtime.getRuntime().availableProcessors();
                int matrixArea = width * height;
                DEFAULT_GRANULARITY = matrixArea / cores;
                int granularity = DEFAULT_GRANULARITY;

                Region[][] a = new Region[width][height];
                Region[][] b = new Region[width][height];
                // Initialize all elements to small value
                double smallVal = 0.005;
                for (int i = 0; i < width; ++i) {
                    for (int j = 0; j < height; ++j) {
                        a[i][j] = new Region(smallVal);
                        b[i][j] = new Region(smallVal);
                    }
                }
                // heat corners
                a[0][0] = new Region(S_TEMP, true);
                a[width-1][height-1] = new Region(T_TEMP, true);
                b[0][0] = new Region(S_TEMP, true);
                b[width-1][height-1] = new Region(T_TEMP, true);

                Driver driver = new Driver(a, b, 0, height, 0, width, granularity);
                driver.invoke();
            }

            abstract class MatrixTree extends CountedCompleter<Void> {
                // maximum difference between old and new values
                double lowestTemp;

                MatrixTree(CountedCompleter<?> p, int c) {
                    super(p, c);
                }
            }

            final class LeafNode extends MatrixTree {
                final Region[][] A; // matrix to get old values from
                final Region[][] B; // matrix to put new values into

                // indices of current submatrix
                final int loRow;
                final int hiRow;
                final int loCol;
                final int hiCol;

                int steps = 0; // track even/odd steps

                LeafNode(CountedCompleter<?> p,
                         Region[][] A, Region[][] B,
                         int loRow, int hiRow,
                         int loCol, int hiCol) {
                    super(p, 0);
                    this.A = A;
                    this.B = B;
                    this.loRow = loRow;
                    this.hiRow = hiRow-1;
                    this.loCol = loCol;
                    this.hiCol = hiCol-1;
                }

                public final void compute() {
                    boolean AtoB = (steps++ & 1) == 0;  // bitwise AND to determine if even or odd
                    Region[][] a = AtoB ? A : B;        // lambda expression form saying if(odd){ a = A } else{ a = B }
                    Region[][] b = AtoB ? B : A;        // same as above but inverse, so if(odd){ b = B } else{ b = A }

                    double convergenceTemp, hottestCorner;
                    hottestCorner = Math.max(S_TEMP, T_TEMP);
                    if (redhottemp < hottestCorner)
                        convergenceTemp = redhottemp;
                    else
                        convergenceTemp = hottestCorner;
                    double lt = convergenceTemp;    // local for tracking lowest temp (set to max so it'll always find the lowest temp that's actually in the set being observed)
                    for (int i = loCol; i <= hiCol; ++i) {
                        for (int j = loRow; j <= hiRow; ++j) {
                            Region curr = a[i][j];
                            if (!curr.isHeatsource) {
                                if (curr.temp < convergenceTemp) {
                                    Region[] neighbors = findNeighbors(i, j, a);
                                    double newTemp = calcNewTemp(neighbors);
                                    b[i][j] = new Region(newTemp);
                                    if (newTemp < lt) lt = newTemp;
                                }
                            }
                            if ((steps & 1) == 0)
                                publish(new MetalBar(b));
                            else
                                publish(new MetalBar(a));
                        }
                    }
                    lowestTemp = lt;
                    tryComplete();
                }

                // Calculate the new temperature of a region by measuring the temperatures of its neighbors based on what metals (and how much of them) are in each neighbor.
                // All regions are set to contain the same ratios of each metal (so this doesn't iteratively check how much of each metal is in each neighbor),
                // using the first neighbor for the baseline. Could be changed though.
                private double calcNewTemp(Region[] neighbors) {
                    double aFactor, bFactor, cFactor;
                    // metal A
                    aFactor = metalTemp(A_CONSTANT, neighbors[0].A_RATIO, neighbors);
                    // metal B
                    bFactor = metalTemp(B_CONSTANT, neighbors[0].B_RATIO, neighbors);
                    // metal C
                    cFactor = metalTemp(C_CONSTANT, neighbors[0].C_RATIO, neighbors);

                    double newTemp = aFactor + bFactor + cFactor;
                    // calculation will heat regions to temps higher than all the regions surrounding that region, so this
                    // makes sure that the region can't get hotter than the regions around it
                    double hottestNeighbor = neighbors[0].temp;
                    for(int i = 1; i < neighbors.length-1; i++) {
                        if(neighbors[i].temp > hottestNeighbor)
                            hottestNeighbor = neighbors[i].temp;
                    }
                    return Math.min(newTemp, hottestNeighbor);
                }

                private double metalTemp(double constant, double ratio, Region[] neighbors) {
                    double newTemp = 0.0;
                    // sum of temps of neighbors * percentage of metal in that neighbor
                    for (Region neighbor : neighbors)
                        newTemp += neighbor.temp * ratio;
                    // multiply newTemp by the thermal constant for that metal, then divide by the number of neighboring regions
                    return (constant * newTemp / neighbors.length);
                }

                private Region[] findNeighbors(int i, int j, Region[][] a) {
                    int maxwidth = width - 1;
                    int maxheight = height - 1;
                    Region[] neighbors;
                    if (i == 0 || i == maxwidth) {
                        if (j == 0 || j == maxheight) {
                            // corner (3 neighbors)
                            neighbors = new Region[3];
                            return getCornerNeighbors(neighbors, i, j, a);
                        } else {
                            // top or bottom edge (5 neighbors)
                            neighbors = new Region[5];
                            return getEdgeNeighbors(neighbors, i, j, a);
                        }
                    } else if (j == 0 || j == maxheight) {
                        // left or right edge (5 neighbors)
                        neighbors = new Region[5];
                        return getEdgeNeighbors(neighbors, i, j, a);
                    } else {
                        // inner (8 neighbors)
                        neighbors = new Region[8];
                        return getInnerNeighbors(neighbors, i, j, a);
                    }
                }

                private Region[] getCornerNeighbors(Region[] neighbors, int x, int y, Region[][] a) {
                    int maxwidth = width - 1;
                    int maxheight = height -1;
                    if (x == 0 && y == 0) {
                        neighbors[0] = a[0][1];
                        neighbors[1] = a[1][0];
                        neighbors[2] = a[1][1];
                        return neighbors;
                    } else if (x == 0 && y == maxheight) {
                        neighbors[0] = a[0][maxheight - 1];
                        neighbors[1] = a[1][maxheight];
                        neighbors[2] = a[1][maxheight - 1];
                        return neighbors;
                    } else if (x == maxwidth && y == 0) {
                        neighbors[0] = a[maxwidth][1];
                        neighbors[1] = a[maxwidth - 1][0];
                        neighbors[2] = a[maxwidth - 1][1];
                        return neighbors;
                    } else {
                        neighbors[0] = a[maxwidth][maxheight - 1];
                        neighbors[1] = a[maxwidth - 1][maxheight];
                        neighbors[2] = a[maxwidth - 1][maxheight - 1];
                        return neighbors;
                    }
                }

                private Region[] getEdgeNeighbors(Region[] neighbors, int x, int y, Region[][] a) {
                    int maxwidth = width - 1;
                    int maxheight = height -1;
                    if (x == 0) {
                        neighbors[0] = a[0][y - 1];
                        neighbors[1] = a[1][y - 1];
                        neighbors[2] = a[1][y];
                        neighbors[3] = a[1][y + 1];
                        neighbors[4] = a[0][y + 1];
                        return neighbors;
                    } else if (x == maxwidth) {
                        neighbors[0] = a[maxwidth][y + 1];
                        neighbors[1] = a[maxwidth - 1][y + 1];
                        neighbors[2] = a[maxwidth - 1][y];
                        neighbors[3] = a[maxwidth - 1][y - 1];
                        neighbors[4] = a[maxwidth][y - 1];
                        return neighbors;
                    } else if (y == 0) {
                        neighbors[0] = a[x - 1][0];
                        neighbors[1] = a[x - 1][1];
                        neighbors[2] = a[x][1];
                        neighbors[3] = a[x + 1][1];
                        neighbors[4] = a[x + 1][0];
                        return neighbors;
                    } else {
                        neighbors[0] = a[x + 1][maxheight];
                        neighbors[1] = a[x + 1][maxheight - 1];
                        neighbors[2] = a[x][maxheight - 1];
                        neighbors[3] = a[x - 1][maxheight - 1];
                        neighbors[4] = a[x - 1][maxheight];
                        return neighbors;
                    }
                }

                private Region[] getInnerNeighbors(Region[] neighbors, int x, int y, Region[][] a) {
                    neighbors[0] = a[x - 1][y + 1];
                    neighbors[1] = a[x - 1][y];
                    neighbors[2] = a[x - 1][y - 1];
                    neighbors[3] = a[x][y - 1];
                    neighbors[4] = a[x + 1][y - 1];
                    neighbors[5] = a[x + 1][y];
                    neighbors[6] = a[x + 1][y + 1];
                    neighbors[7] = a[x][y + 1];
                    return neighbors;
                }
            }

             final class FourNode extends MatrixTree {
                MatrixTree q1;
                MatrixTree q2;
                MatrixTree q3;
                MatrixTree q4;

                FourNode(CountedCompleter<?> p) {
                    super(p, 3);
                }

                public void onCompletion(CountedCompleter<?> caller) {
                    double lt = q1.lowestTemp, l;
                    if ((l = q2.lowestTemp) < lt)
                        lt = l;
                    if ((l = q3.lowestTemp) < lt)
                        lt = l;
                    if ((l = q4.lowestTemp) < lt)
                        lt = l;
                    lowestTemp = lt;
                    setPendingCount(3);
                }

                public final void compute() {
                    q4.fork();
                    q3.fork();
                    q2.fork();
                    q1.compute();
                }
            }

            final class TwoNode extends MatrixTree {
                MatrixTree q1;
                MatrixTree q2;

                TwoNode(CountedCompleter<?> p) {
                    super(p, 1);
                }

                public void onCompletion(CountedCompleter<?> caller) {
                    double lt = q1.lowestTemp, l;
                    if ((l = q2.lowestTemp) < lt)
                        lt = l;
                    lowestTemp = lt;
                    setPendingCount(1);
                }

                public final void compute() {
                    q2.fork();
                    q1.compute();
                }
            }

            final class Driver extends RecursiveAction {
                final MatrixTree mat;
                final Region[][] A, B;
                final int firstRow, lastRow, firstCol, lastCol;
                int nleaf;
                final int leafs;

                Driver(Region[][] A, Region[][] B,
                       int firstRow, int lastRow,
                       int firstCol, int lastCol,
                       int leafs) {
                    this.A = A;
                    this.B = B;
                    this.firstRow = firstRow;
                    this.firstCol = firstCol;
                    this.lastRow = lastRow;
                    this.lastCol = lastCol;
                    this.leafs = leafs;
                    mat = build(null, A, B, firstRow, lastRow, firstCol, lastCol, leafs);
                }

                MatrixTree build(MatrixTree p,
                                 Region[][] a, Region[][] b,
                                 int loRo, int hiRo, int loCol, int hiCol, int leafs) {
                    int rows = (hiRo - loRo);
                    int cols = (hiCol - loCol);

                    int midrow = (loRo + hiRo) >>> 1; // midpoints
                    int midcol = (loCol + hiCol) >>> 1;

                    int hrows = (midrow - loRo);
                    int hcols = (midcol - loCol);

                    if (rows * cols <= leafs) {
                        ++nleaf;
                        return new LeafNode(p, a, b, loRo, hiRo, loCol, hiCol);
                    } else if (hrows * hcols >= leafs) {
                        FourNode q = new FourNode(p);
                        q.q1 = build(q, a, b, loRo, midrow, loCol, midcol, leafs);  // quadrant 2 _|
                        q.q2 = build(q, a, b, loRo, midrow, midcol, hiCol, leafs);  // quadrant 1 |_
                        q.q3 = build(q, a, b, midrow, hiRo, loCol, midcol, leafs);  // quadrant 3
                        q.q4 = build(q, a, b, midrow, hiRo, midcol, hiCol, leafs);  // quadrant 4
                        return q;
                    } else if (cols >= rows) {
                        TwoNode q = new TwoNode(p);
                        q.q1 = build(q, a, b, loRo, hiRo, loCol, midcol, leafs);
                        q.q2 = build(q, a, b, loRo, hiRo, midcol, hiCol, leafs);
                        return q;
                    } else {
                        TwoNode q = new TwoNode(p);
                        q.q1 = build(q, a, b, loRo, midrow, loCol, hiCol, leafs);
                        q.q2 = build(q, a, b, midrow, hiRo, loCol, hiCol, leafs);
                        return q;
                    }
                }

                void doCompute(MatrixTree m) {
                    double convergenceTemp, cornerHeatAvg = (S_TEMP + T_TEMP) / 2;
                    //ExecutorService pool = Executors.newWorkStealingPool();

                    if (redhottemp < cornerHeatAvg)
                        convergenceTemp = redhottemp * .90;
                    else
                        convergenceTemp = cornerHeatAvg;
                    while (m.lowestTemp < convergenceTemp) {
                        //System.out.println(m.lowestTemp);
                        m.setPendingCount(3);
                        //pool.submit(m::invoke);
                        m.invoke();
                        m.reinitialize();
                    }
                    System.out.println("converged");
                }

                public void compute() {
                    paintMe = new MetalBar(A);
                    paintImmediately(0,0,width*10,height*10);
                    doCompute(mat);
                }
            }
        };
        workerThread.execute();
    }
}