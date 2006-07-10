// $Id: IFPSConvention.java,v 1.3 2006/01/14 22:15:02 caron Exp $
/*
 * Copyright 1997-2006 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.nc2.dataset.conv;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.dataset.*;
import ucar.nc2.util.CancelTask;
import ucar.unidata.geoloc.projection.LambertConformal;
import ucar.unidata.geoloc.ProjectionPointImpl;
import ucar.unidata.geoloc.LatLonPointImpl;
import ucar.unidata.geoloc.Projection;

import java.io.IOException;
import java.util.*;

/**
 * IFPS Convention Allows Local NWS forecast office generated forecast datasets to be brought into IDV.
 * @author Burks
 * @version $Revision: 1.3 $ $Date: 2006/01/14 22:15:02 $
 */

public class IFPSConvention extends CoordSysBuilder {

  /** return true if we think this is a IFPS file. */
  public static boolean isMine( NetcdfFile ncfile) {
    Variable v = ncfile.findVariable("latitude");

    return (null != ncfile.findDimension("DIM_0")) && (null != ncfile.findVariable("longitude"))
            && (null != v) && (null != ncfile.findAttValueIgnoreCase(v, "projectionType", null));
  }

  private Variable projVar = null; // use this to get projection info
  public void augmentDataset( NetcdfDataset ds, CancelTask cancelTask) {
    this.conventionName = "IFPS";
    parseInfo.append("IFPS augmentDataset \n");

   // Figure out projection info. Assume the same for all variables
    Variable lonVar = ds.findVariable("longitude");
    lonVar.addAttribute( new Attribute("units", "degrees_east"));
    lonVar.addAttribute( new Attribute("_CoordinateAxisType", "Lon"));
    Variable latVar = ds.findVariable("latitude");
    latVar.addAttribute( new Attribute("_CoordinateAxisType", "Lat"));
    latVar.addAttribute( new Attribute("units", "degrees_north"));

    projVar = latVar;
    String projName = ds.findAttValueIgnoreCase(projVar, "projectionType", null);
    if (projName.equals("LAMBERT_CONFORMAL")) {
      Projection proj = makeLCProjection( ds);

      try {
        makeXYcoords( ds, proj, latVar, lonVar);
      } catch (IOException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }

    }

    // figure out the time coordinate for each data variable
    // LOOK : always seperate; could try to discover if they are the same
    Iterator vars = ds.getVariables().iterator();
    while (vars.hasNext()) {
        VariableDS ncvar = (VariableDS) vars.next();
        //variables that are used but not displayable or have no data have DIM_0, also don't want history, since those are just how the person edited the grids
        if ((ncvar.getDimension(0).getName().equals("DIM_0") != true) && !ncvar.getName().endsWith("History")
              && (ncvar.getRank() > 2) && !ncvar.getName().startsWith("Tool")) {
            createTimeCoordinate(ds,ncvar);
        } else if (ncvar.getName().equals("Topo")){
            //Deal with Topography variable
            ncvar.addAttribute(new Attribute("long_name", "Topography"));
            ncvar.addAttribute(new Attribute("units", "ft"));
        }
    }

    ds.finish();
  }

  private void createTimeCoordinate(NetcdfDataset ds,VariableDS ncVar){
    //Time coordinate is stored in the attribute validTimes
    //One caveat is that the times have two bounds and upper and a lower

    // get the times values
    Attribute timesAtt = ncVar.findAttribute("validTimes");
    if (timesAtt == null) return;
    Array timesArray = timesAtt.getValues();

    // get every other one LOOK this is awkward
    try {
      int n = (int) timesArray.getSize();
      ArrayList list = new ArrayList();
      list.add(new Range(0, n-1, 2));
      timesArray = timesArray.section(list);
    } catch (InvalidRangeException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }

    // make sure it matches the dimension
    DataType dtype = DataType.getType( timesArray.getElementType());
    int nTimesAtt = (int) timesArray.getSize();

    // create a special dimension and coordinate variable
    Dimension dimTime = ncVar.getDimension(0);
    int nTimesDim = dimTime.getLength();
    if (nTimesDim != nTimesAtt) {
      parseInfo.append(" **error ntimes in attribute ("+nTimesAtt+") doesnt match dimension length ("+
          nTimesDim+") for variable "+ ncVar.getName()+"\n");
      return;
    }

    // add the dimension
    String dimName = ncVar.getName()+"_timeCoord";
    Dimension newDim = new Dimension(dimName, nTimesDim, true);
    ds.addDimension( null, newDim);

    // add the coordinate variable
    String units = "seconds since 1970-1-1 00:00:00";
    String desc = "time coordinate for "+ncVar.getName();

    CoordinateAxis1D timeCoord = new CoordinateAxis1D( ds, null, dimName, dtype, dimName, units, desc);
    timeCoord.setCachedData(timesArray, true);
    timeCoord.addAttribute(new Attribute("long_name",  desc));
    timeCoord.addAttribute(new Attribute("units",  units));
    timeCoord.addAttribute(new Attribute("_CoordinateAxisType", "Time"));
    ds.addCoordinateAxis(timeCoord);

    parseInfo.append(" added coordinate variable "+ dimName+"\n");

    // now make the original variable use the new dimension
    List dimsList = ncVar.getDimensions();
    dimsList.set(0, newDim);
    ncVar.setDimensions( dimsList);

    // better to explicitly set the coordinate system
    ncVar.addAttribute(new Attribute("_CoordinateAxes", dimName+" yCoord xCoord"));

    // fix the attributes
    Attribute att = ncVar.findAttribute("fillValue");
    if (att != null)
      ncVar.addAttribute(new Attribute("_FillValue", att.getNumericValue()));
    att = ncVar.findAttribute("descriptiveName");
    if (null != att)
      ncVar.addAttribute(new Attribute("long_name", att.getStringValue()));

    // ncVar.enhance();
  }

  protected String getZisPositive( NetcdfDataset ds, CoordinateAxis v) {
    return "up";
  }

  private Projection makeLCProjection(NetcdfDataset ds) {
    Attribute latLonOrigin = projVar.findAttributeIgnoreCase("latLonOrigin");
    double centralLon = latLonOrigin.getNumericValue(0).doubleValue();
    double centralLat = latLonOrigin.getNumericValue(1).doubleValue();

    double par1 = findAttributeDouble( "stdParallelOne");
    double par2 = findAttributeDouble( "stdParallelTwo");
    LambertConformal lc = new LambertConformal(centralLat, centralLon, par1, par2);

    // make Coordinate Transform Variable
    ProjectionCT ct = new ProjectionCT("lambertConformalProjection", "FGDC", lc);
    VariableDS ctVar = makeCoordinateTransformVariable(ds, ct);
    ctVar.addAttribute( new Attribute("_CoordinateAxes", "xCoord yCoord"));
    ds.addVariable(null, ctVar);

    return lc;
  }

  private void makeXYcoords(NetcdfDataset ds, Projection proj, Variable latVar, Variable lonVar) throws IOException {
    // brute force
    Array latData = latVar.read();
    Array lonData = lonVar.read();

    Dimension y_dim = latVar.getDimension(0);
    Dimension x_dim = latVar.getDimension(1);

    Array xData = Array.factory( float.class, new int[] {x_dim.getLength()});
    Array yData = Array.factory( float.class, new int[] {y_dim.getLength()});

    LatLonPointImpl latlon = new LatLonPointImpl();
    ProjectionPointImpl pp = new ProjectionPointImpl();

    Index latlonIndex = latData.getIndex();
    Index xIndex = xData.getIndex();
    Index yIndex = yData.getIndex();

    // construct x coord
    for (int i=0; i<x_dim.getLength(); i++) {
      double lat = latData.getDouble( latlonIndex.set1(i));
      double lon = lonData.getDouble( latlonIndex);
      latlon.set( lat, lon);
      proj.latLonToProj( latlon, pp);
      xData.setDouble( xIndex.set(i), pp.getX());
    }

    // construct y coord
    for (int i=0; i<y_dim.getLength(); i++) {
      double lat = latData.getDouble( latlonIndex.set0(i));
      double lon = lonData.getDouble( latlonIndex);
      latlon.set( lat, lon);
      proj.latLonToProj( latlon, pp);
      yData.setDouble( yIndex.set(i), pp.getY());
    }

    VariableDS xaxis = new VariableDS(ds, null, null, "xCoord", DataType.FLOAT, x_dim.getName(), "km", "x on projection");
    xaxis.addAttribute(new Attribute("units", "km"));
    xaxis.addAttribute(new Attribute("long_name", "x on projection"));
    xaxis.addAttribute(new Attribute("_CoordinateAxisType", "GeoX"));

    VariableDS yaxis = new VariableDS(ds, null, null, "yCoord", DataType.FLOAT, y_dim.getName(), "km", "y on projection");
    yaxis.addAttribute(new Attribute("units", "km"));
    yaxis.addAttribute(new Attribute("long_name", "y on projection"));
    yaxis.addAttribute(new Attribute("_CoordinateAxisType", "GeoY"));

    xaxis.setCachedData( xData, true);
    yaxis.setCachedData( yData, true);

    ds.addVariable( null, xaxis);
    ds.addVariable( null, yaxis);
  }

   private double findAttributeDouble(String attname) {
    Attribute att = projVar.findAttributeIgnoreCase(attname);
    return att.getNumericValue().doubleValue();
  }

}