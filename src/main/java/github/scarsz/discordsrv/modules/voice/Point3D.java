/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2022 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.modules.voice;
import org.apache.commons.math3.ml.clustering.Clusterable;

public class Point3D implements Clusterable {
    private final double[] point;

    public Point3D(double x, double y, double z) {
        this.point = new double[]{x, y, z};
    }

    @Override
    public double[] getPoint() {
        return point;
    }

    @Override
    public String toString() {
        return "x: " + point[0] + ", y: " + point[1] + ", z: " + point[2];
    }

}