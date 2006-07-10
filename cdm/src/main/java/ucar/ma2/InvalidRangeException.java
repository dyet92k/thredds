// $Id: InvalidRangeException.java,v 1.2 2004/07/12 23:40:14 caron Exp $
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
package ucar.ma2;

/**
   Thrown if an attempt is made to use an invalid
   Range to index an array.
 *
 * @author caron
 * @version $Revision: 1.18 $ $Date: 2006/05/24 00:12:56 $
 */

public final class InvalidRangeException extends Exception {
  public InvalidRangeException() { super(); }
  public InvalidRangeException(String s) { super(s); }
}