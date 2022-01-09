package com.company;

// Jacobi iteration on a mesh. Based loosely on a Filaments demo

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.*;

public class CCJacobi {
    final  double A_CONSTANT = 0.75, B_CONSTANT = 1.0, C_CONSTANT = 1.25,
                        S_TEMP = 1000.0, T_TEMP = 400.0;
     int width, height;
     float redhottemp;
     Graphics g;
     boolean painterStarted = false;
    ExecutorService pool = Executors.newWorkStealingPool();

    private static class Region {
        final double A_RATIO = 0.33, B_RATIO = 0.3, C_RATIO = 0.37;
        double temp;
        boolean heatsource = false;
        public Region(double temp) {
            this.temp = temp;
        }
        public Region(double temp, boolean heatsource) {
            this.temp = temp;
            this.heatsource = heatsource;
        }
    }

    //    final int DEFAULT_GRANULARITY = 4096;
    //    final int DEFAULT_GRANULARITY = 256;
    final int DEFAULT_GRANULARITY;

    /**
     * The maximum number of matrix cells
     * at which to stop recursing down and instead directly update.
     */

    CCJacobi(Graphics g, float redhottemp, int width, int height) {
        this.redhottemp = redhottemp;
        this.g = g;
        this.width = width;
        this.height = height;
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

    abstract  class MatrixTree extends CountedCompleter<Void> {
        // maximum difference between old and new values
        double lowestTemp; // change to lowestTemp

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

            double convergenceTemp;
            if (S_TEMP > T_TEMP)
                convergenceTemp = S_TEMP * .99;
            else
                convergenceTemp = T_TEMP * .99;
            double lt = convergenceTemp;    // local for tracking lowest temp (set to max so it'll always find the lowest temp that's actually in the set being observed)
            for (int i = loCol; i <= hiCol; ++i) {
                for (int j = loRow; j <= hiRow; ++j) {
                    Region regionLocal = a[i][j];
                    if (!regionLocal.heatsource) {
                        if (regionLocal.temp < convergenceTemp) {
                            Region[] neighbors = findNeighbors(i, j, a);
                            double newTemp = calcNewTemp(neighbors);
                            b[i][j] = new Region(newTemp);
                            if (newTemp < lt) lt = newTemp;
                        }
                    }
                    if (!painterStarted) {
                        Timer t = new Timer(10, e -> updateDisplay());
                        t.start();
                    }
                    //paintRegion((float) newTemp, i, j); // remove for implementation of updateDisplay which repaints whole matrix
                }
            }
            lowestTemp = lt;
            tryComplete();
        }

        public void updateDisplay() {
            Region[][] r;
            if ((steps & 1) == 0) {
                r = B;
            } else {
                r = A;
            }
            // paint r
            for(int i = 0; i < width; i++) {
                for(int j = 0; j < height; j++) {
                    paintRegion((float)r[i][j].temp, i, j);
                }
            }
        }

        private void paintRegion(float regionTemp, int x, int y) {
            // in HSB, 0.6 results in blue while red is at 0 and 1. this formula bounds the result using the regional
            // temp to the range of .6 to 0, and can be altered to become red at a different point by changing redhottemp.
            float hue = ((redhottemp-regionTemp) / redhottemp) * .7f;

            if(hue < 0) // 0 = red
                hue = 0;
            Color c = Color.getHSBColor(hue, .8f, 1);
            g.setColor(c);
            g.fillRect(x * 10, y * 10,10, 10);
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

            return (aFactor + bFactor + cFactor);
        }

        private double metalTemp(double constant, double ratio, Region[] neighbors) {
            double newTemp = 0.0;
            // sum of temps of neighbors * percentage of metal in that neighbor
            for (Region neighbor : neighbors)
                newTemp += neighbor.temp * ratio;
            // multiply newTemp by the thermal constant for that metal, then divide by the number of neighboring regions
            return constant * newTemp / neighbors.length;
        }

        private Region[] findNeighbors(int i, int j, Region[][] a) {
            int maxWidth = width - 1;
            int maxHeight = height - 1;
            Region[] neighbors;
            if (i == 0 || i == maxWidth) {
                if (j == 0 || j == maxHeight) {
                    // corner (3 neighbors)
                    neighbors = new Region[3];
                    return getCornerNeighbors(neighbors, i, j, a);
                } else {
                    // edge (5 neighbors)
                    neighbors = new Region[5];
                    return getEdgeNeighbors(neighbors, i, j, a);
                }
            } else if (j == 0 || j == maxHeight) {
                // edge (5 neighbors)
                neighbors = new Region[5];
                return getEdgeNeighbors(neighbors, i, j, a);
            } else {
                // inner (8 neighbors)
                neighbors = new Region[8];
                return getInnerNeighbors(neighbors, i, j, a);
            }
        }

        private Region[] getCornerNeighbors(Region[] neighbors, int x, int y, Region[][] a) {
            int maxWidth = width - 1;
            int maxHeight = height -1;
            if (x == 0 && y == 0) {
                neighbors[0] = a[0][1];
                neighbors[1] = a[1][0];
                neighbors[2] = a[1][1];
                return neighbors;
            } else if (x == 0 && y == maxHeight) {
                neighbors[0] = a[0][maxHeight - 1];
                neighbors[1] = a[1][maxHeight];
                neighbors[2] = a[1][maxHeight - 1];
                return neighbors;
            } else if (x == maxWidth && y == 0) {
                neighbors[0] = a[maxWidth][1];
                neighbors[1] = a[maxWidth - 1][0];
                neighbors[2] = a[maxWidth - 1][1];
                return neighbors;
            } else {
                neighbors[0] = a[maxWidth][maxHeight - 1];
                neighbors[1] = a[maxWidth - 1][maxHeight];
                neighbors[2] = a[maxWidth - 1][maxHeight - 1];
                return neighbors;
            }
        }

        private Region[] getEdgeNeighbors(Region[] neighbors, int x, int y, Region[][] a) {
            int maxWidth = width - 1;
            int maxHeight = height -1;
            if (x == 0) {
                neighbors[0] = a[0][y - 1];
                neighbors[1] = a[1][y - 1];
                neighbors[2] = a[1][y];
                neighbors[3] = a[1][y + 1];
                neighbors[4] = a[0][y + 1];
                return neighbors;
            } else if (x == maxWidth) {
                neighbors[0] = a[maxWidth][y + 1];
                neighbors[1] = a[maxWidth - 1][y + 1];
                neighbors[2] = a[maxWidth - 1][y];
                neighbors[3] = a[maxWidth - 1][y - 1];
                neighbors[4] = a[maxWidth][y - 1];
                return neighbors;
            } else if (y == 0) {
                neighbors[0] = a[x - 1][0];
                neighbors[1] = a[x - 1][1];
                neighbors[2] = a[x][1];
                neighbors[3] = a[x + 1][1];
                neighbors[4] = a[x + 1][0];
                return neighbors;
            } else {
                neighbors[0] = a[x + 1][maxHeight];
                neighbors[1] = a[x + 1][maxHeight - 1];
                neighbors[2] = a[x][maxHeight - 1];
                neighbors[3] = a[x - 1][maxHeight - 1];
                neighbors[4] = a[x - 1][maxHeight];
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
        MatrixTree mat;
        Region[][] A, B;
        int firstRow, lastRow, firstCol, lastCol, nleaf;
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
            double convergenceTemp = redhottemp * .99;
            while (m.lowestTemp < convergenceTemp) {
                m.setPendingCount(3);
                m.invoke();
                m.reinitialize();
            }
            System.out.println("convergence check");
        }

        public void compute() {
            doCompute(mat);
        }
    }
}