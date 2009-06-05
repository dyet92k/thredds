/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.ft.point.standard.plug;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.constants.CF;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.constants.AxisType;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.ft.point.standard.*;
import ucar.nc2.*;
import ucar.ma2.Array;
import ucar.ma2.DataType;

import java.util.*;
import java.io.IOException;

/**
 * Class Description.
 *
 * @author caron
 * @since Mar 3, 2009
 */
public class GempakCdm extends TableConfigurerImpl {

  private final String Convention = "GEMPAK/CDM";

  public boolean isMine(FeatureType wantFeatureType, NetcdfDataset ds) {
    boolean ok = false;
    String conv = ds.findAttValueIgnoreCase(null, "Conventions", null);
    if (conv == null) return false;
    if (conv.equals(Convention)) ok = true;

    StringTokenizer stoke = new StringTokenizer(conv, ",");
    while (stoke.hasMoreTokens()) {
      String toke = stoke.nextToken().trim();
      if (toke.equals(Convention))
        ok = true;
    }
    if (!ok) return false;

    String ftypeS = ds.findAttValueIgnoreCase(null, CF.featureTypeAtt, null);
    CF.FeatureType ftype = (ftypeS == null) ? CF.FeatureType.point : CF.FeatureType.valueOf(ftypeS);
    return (ftype == CF.FeatureType.stationTimeSeries);
  }

  public TableConfig getConfig(FeatureType wantFeatureType, NetcdfDataset ds, Formatter errlog) throws IOException {

    String ftypeS = ds.findAttValueIgnoreCase(null, CF.featureTypeAtt, null);
    CF.FeatureType ftype = (ftypeS == null) ? CF.FeatureType.point : CF.FeatureType.valueOf(ftypeS);
    switch (ftype) {
      case point:
        return null; // use default handler
      case stationTimeSeries:
        if (wantFeatureType == FeatureType.POINT)
          return getStationAsPointConfig(ds, errlog);
        else
          return getStationConfig(ds, errlog);
      default:
        throw new IllegalStateException("unimplemented feature ftype= " + ftype);
    }
  }

  protected TableConfig getStationConfig(NetcdfDataset ds, Formatter errlog) throws IOException {
    boolean needFinish = false;

    // find lat coord
    Variable lat = CoordSysEvaluator.findCoordByType(ds, AxisType.Lat);
    if (lat == null) {
      errlog.format("Must have a Latitude coordinate");
      return null;
    }

    // find lon coord
    Variable lon = CoordSysEvaluator.findCoordByType(ds, AxisType.Lon);
    if (lon == null) {
      errlog.format("Must have a Longitude coordinate");
      return null;
    }

    if (lat.getRank() != lon.getRank()) {
      errlog.format("Lat and Lon coordinate must have same rank");
      return null;
    }

    // check dimensions
    boolean stnIsScalar = (lat.getRank() == 0);
    boolean stnIsSingle = (lat.getRank() == 1) && (lat.getSize() == 1);
    Dimension stationDim = null;

    if (!stnIsScalar) {
      if (lat.getDimension(0) != lon.getDimension(0)) {
        errlog.format("Lat and Lon coordinate must have same size");
        return null;
      }
      stationDim = lat.getDimension(0);
    }

    boolean hasStruct = Evaluator.hasRecordStructure(ds);

    Table.Type stationTableType = stnIsScalar ? Table.Type.Top : Table.Type.Structure;
    TableConfig stnTable = new TableConfig(stationTableType, "station");
    stnTable.featureType = FeatureType.STATION;
    stnTable.isPsuedoStructure = !hasStruct;
    stnTable.dim = stationDim;

    stnTable.lat= lat.getName();
    stnTable.lon= lon.getName();

    // optional alt coord
    Variable alt = CoordSysEvaluator.findCoordByType(ds, AxisType.Height);
    if (alt != null)
      stnTable.stnAlt = alt.getName();

    // station id
    stnTable.stnId = Evaluator.getVariableWithAttribute(ds, "standard_name", "station_id");
    if (stnTable.stnId == null) {
      errlog.format("Must have a Station id variable with standard name station_id");
      return null;
    }
    Variable stnId = ds.findVariable(stnTable.stnId);

    if (!stnIsScalar) {
      if (!stnId.getDimension(0).equals(stationDim)) {
        errlog.format("Station id outer dimension must match latitude/longitude dimension");
        return null;
      }
    }

    // obs table
    Variable time = CoordSysEvaluator.findCoordByType(ds, AxisType.Time);
    if (time == null) {
      errlog.format("Must have a Time coordinate");
      return null;
    }
    Dimension obsDim = time.getDimension(time.getRank()-1); // may be time(time) or time(stn, obs)

    Table.Type obsTableType = null;
    Structure multidimStruct = null;
    if (obsTableType == null) {
      // Structure(station, time)
      multidimStruct = Evaluator.getStructureWithDimensions(ds, stationDim, obsDim);
      if (multidimStruct != null) {
        obsTableType = Table.Type.MultiDimStructure;
      }
    }

    // multidim case
    if (obsTableType == null) {
      // time(station, time)
      if (time.getRank() == 2) {
        obsTableType = Table.Type.MultiDimInner;
      }
    }

    if (obsTableType == null) {
        errlog.format("Cannot figure out Station/obs table structure");
        return null;
    }

    TableConfig obs = new TableConfig(obsTableType, obsDim.getName());
    obs.dim = obsDim;
    obs.time = time.getName();
    stnTable.addChild(obs);

    if ((obsTableType == Table.Type.Structure) || (obsTableType == Table.Type.Contiguous) ||
      (obsTableType == Table.Type.ParentIndex)) {
      obs.structName = hasStruct ? "record" : obsDim.getName();
      obs.isPsuedoStructure = !hasStruct;
    }

    if (obsTableType == Table.Type.MultiDimStructure) {
      obs.structName = multidimStruct.getName();
      obs.isPsuedoStructure = false;
      // if time is not in this structure, need to join it
      if (multidimStruct.findVariable( time.getShortName()) == null) {
        obs.addJoin(new JoinArray( time, JoinArray.Type.raw, 0));
      }
    }

    if (obsTableType == Table.Type.MultiDimInner) {
      obs.dim = obsDim;
    }

    if (needFinish) ds.finish();
    return stnTable;
  }

  protected TableConfig getStationAsPointConfig(NetcdfDataset ds, Formatter errlog) throws IOException {
    boolean needFinish = false;

    // find lat coord
    Variable lat = CoordSysEvaluator.findCoordByType(ds, AxisType.Lat);
    if (lat == null) {
      errlog.format("Must have a Latitude coordinate");
      return null;
    }

    // find lon coord
    Variable lon = CoordSysEvaluator.findCoordByType(ds, AxisType.Lon);
    if (lon == null) {
      errlog.format("Must have a Longitude coordinate");
      return null;
    }

    if (lat.getRank() != lon.getRank()) {
      errlog.format("Lat and Lon coordinate must have same rank");
      return null;
    }

    // check dimensions
    boolean stnIsScalar = (lat.getRank() == 0);
    boolean stnIsSingle = (lat.getRank() == 1) && (lat.getSize() == 1);
    Dimension stationDim = null;

    if (!stnIsScalar) {
      if (lat.getDimension(0) != lon.getDimension(0)) {
        errlog.format("Lat and Lon coordinate must have same size");
        return null;
      }
      stationDim = lat.getDimension(0);
    }

    // optional alt coord
    Variable alt = CoordSysEvaluator.findCoordByType(ds, AxisType.Height);

    // obs table
    Variable time = CoordSysEvaluator.findCoordByType(ds, AxisType.Time);
    if (time == null) {
      errlog.format("Must have a Time coordinate");
      return null;
    }
    Dimension obsDim = time.getDimension(time.getRank()-1); // may be time(time) or time(stn, obs)

    Table.Type obsTableType = Table.Type.Structure;
    Structure multidimStruct = Evaluator.getStructureWithDimensions(ds, stationDim, obsDim);

    if (multidimStruct == null) {
        errlog.format("Cannot figure out StationAsPoint table structure");
        return null;
    }

    TableConfig obs = new TableConfig(obsTableType, obsDim.getName());
    obs.dim = obsDim;
    obs.structName = multidimStruct.getName();
    obs.isPsuedoStructure = false;
    obs.featureType = FeatureType.POINT;

    obs.lat= lat.getName();
    obs.lon= lon.getName();
    obs.time= time.getName();
    if (alt != null)
       obs.elev = alt.getName();

    List<Variable> vars = new ArrayList<Variable>(30);
    for (Variable v : ds.getVariables()) {
      if ((v.getDimension(0) == stationDim) &&
          ((v.getRank() == 1) || ((v.getRank() == 2) && (v.getDataType() == DataType.CHAR)))) 
          vars.add(v);
    }

    Structure s =   new StructurePseudo(ds, null, "stnStruct", vars, stationDim);
    obs.addJoin(new JoinMuiltdimStructure(s, obsDim.getLength()));
    obs.addJoin(new JoinArray( time, JoinArray.Type.modulo, obsDim.getLength()));

    if (needFinish) ds.finish();
    return obs;
  }
}
