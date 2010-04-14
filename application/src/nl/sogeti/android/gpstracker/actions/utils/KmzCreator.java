/*------------------------------------------------------------------------------
 **     Ident: Delivery Center Java
 **    Author: rene
 ** Copyright: (c) Jan 21, 2010 Sogeti Nederland B.V. All Rights Reserved.
 **------------------------------------------------------------------------------
 ** Sogeti Nederland B.V.            |  No part of this file may be reproduced  
 ** Distributed Software Engineering |  or transmitted in any form or by any        
 ** Lange Dreef 17                   |  means, electronic or mechanical, for the      
 ** 4131 NJ Vianen                   |  purpose, without the express written    
 ** The Netherlands                  |  permission of the copyright holder.
 *------------------------------------------------------------------------------
 */
package nl.sogeti.android.gpstracker.actions.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import nl.sogeti.android.gpstracker.R;
import nl.sogeti.android.gpstracker.db.GPStracking;
import nl.sogeti.android.gpstracker.db.GPStracking.Media;
import nl.sogeti.android.gpstracker.db.GPStracking.Segments;
import nl.sogeti.android.gpstracker.db.GPStracking.Tracks;
import nl.sogeti.android.gpstracker.db.GPStracking.Waypoints;
import nl.sogeti.android.gpstracker.util.Constants;

import org.xmlpull.v1.XmlSerializer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

/**
 * Create a KMZ version of a stored track
 * 
 * @version $Id$
 * @author rene (c) Mar 22, 2009, Sogeti B.V.
 */
public class KmzCreator extends XmlCreator
{
   public static final String NS_SCHEMA = "http://www.w3.org/2001/XMLSchema-instance";
   public static final String NS_KML_22 = "http://www.opengis.net/kml/2.2";
   public static final String DATETIME = "yyyy-MM-dd'T'HH:mm:ss'Z'";
   
   private String mChosenFileName;
   private Intent mIntent;
   private XmlCreationProgressListener mProgressListener;
   private Context mContext;
   private String TAG = "OGT.KmzCreator";
   
   public KmzCreator(Context context, Intent intent, String chosenFileName, XmlCreationProgressListener listener)
   {
      mChosenFileName = chosenFileName;
      mContext = context;
      mIntent = intent;
      mProgressListener = listener;
   }
   
   public void run()
   {
      Looper.prepare();
      Uri trackUri = mIntent.getData();
      String fileName = "UntitledTrack";
      if( mChosenFileName != null && !mChosenFileName.equals( "" ))
      {
         fileName = mChosenFileName;
      }
      else
      {
         Cursor trackCursor = null;
         ContentResolver resolver = mContext.getContentResolver();
         try
         {
            trackCursor = resolver.query( trackUri, new String[] { Tracks.NAME }, null, null, null );
            if( trackCursor.moveToLast() )
            {
               fileName = trackCursor.getString( 0 );
            }
         }
         finally
         {
            if( trackCursor != null )
            {
               trackCursor.close();
            }
         }
      }
    
      if( !( fileName.endsWith( ".kmz" ) || fileName.endsWith( ".zip" ) ) )
      {
         setExportDirectoryPath( Environment.getExternalStorageDirectory() +Constants.EXTERNAL_DIR + fileName );
      }
      else
      {
         setExportDirectoryPath( Environment.getExternalStorageDirectory() +Constants.EXTERNAL_DIR + fileName.substring( 0, fileName.length()-4 ) );
      }
      new File( getExportDirectoryPath() ).mkdirs();
      String xmlFilePath = getExportDirectoryPath()+"/doc.kml";
   
      if( mProgressListener != null )
      {
         mProgressListener.startNotification( fileName );
         mProgressListener.updateNotification( getProgress(), getGoal() );
      }
      
      String resultFilename = xmlFilePath;
      try
      {
         XmlSerializer serializer = Xml.newSerializer();
         BufferedOutputStream buf = new BufferedOutputStream( new FileOutputStream( xmlFilePath ), 8192 );
         serializer.setOutput( buf, "UTF-8" );
         
         serializeTrack( trackUri, fileName, serializer );
         
         if( isNeedsBundling() )
         {
            resultFilename = bundlingMediaAndXml( fileName, ".kmz" );
         }

         fileName = new File( resultFilename ).getName();
   
         CharSequence text = mContext.getString( R.string.ticker_stored )+"\"" + fileName+"\"";
         Toast toast = Toast.makeText( mContext.getApplicationContext(), text, Toast.LENGTH_SHORT );
         toast.show();
         
//         serializer.text( "\n" );
//         serializer.startTag( "", "Document" );
//         serializer.text( "\n" );
//         serializer.endTag( "", "Document" );
      }
      catch (IllegalArgumentException e)
      {
         Log.e( TAG , "Unable to save " + e );
         CharSequence text = mContext.getString( R.string.ticker_failed )+"\"" + xmlFilePath+"\""  + mContext.getString( R.string.error_filename );
         Toast toast = Toast.makeText( mContext.getApplicationContext(), text, Toast.LENGTH_LONG );
         toast.show();
      }
      catch (IllegalStateException e)
      {
         Log.e( TAG, "Unable to save " + e );
         CharSequence text = mContext.getString( R.string.ticker_failed )+"\"" + xmlFilePath+"\""  + mContext.getString( R.string.error_buildxml );
         Toast toast = Toast.makeText( mContext.getApplicationContext(), text, Toast.LENGTH_LONG );
         toast.show();
      }
      catch (IOException e)
      {
         Log.e( TAG, "Unable to save " + e );
         CharSequence text = mContext.getString( R.string.ticker_failed )+"\"" + xmlFilePath+"\""  + mContext.getString( R.string.error_writesdcard );
         Toast toast = Toast.makeText( mContext.getApplicationContext(), text, Toast.LENGTH_LONG );
         toast.show();
      }
      finally
      {
         if( mProgressListener != null )
         {
            mProgressListener.endNotification( xmlFilePath );
         }
         Looper.loop();
      }
   }

   private void serializeTrack( Uri trackUri, String fileName, XmlSerializer serializer ) throws IOException
   {
      serializer.startDocument( "UTF-8", true );
      serializer.setPrefix( "xsi", NS_SCHEMA );
      serializer.setPrefix( "kml", NS_KML_22 );
      serializer.startTag( "", "kml" );
      serializer.attribute( NS_SCHEMA, "schemaLocation", NS_KML_22 + " http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd" );
      serializer.attribute( null, "xmlns", NS_KML_22 );
  
      serializer.text( "\n" );
      serializer.startTag( "", "Document" );
      serializer.text( "\n" );
      serializer.startTag( "", "name" );
      serializer.text( fileName );
      serializer.endTag( "", "name" );
      
      /* from <name/> upto <Folder/>*/
      serializeTrackHeader( serializer, trackUri );
      
      serializer.text( "\n" );
      serializer.endTag( "", "Document" );
      
      serializer.endTag( "", "kml" );
      serializer.endDocument();
   }

   private String serializeTrackHeader( XmlSerializer serializer, Uri trackUri ) throws IOException
   {
      ContentResolver resolver = mContext.getContentResolver();
      Cursor trackCursor = null;
      String name = null;
   
      try
      {
         trackCursor = resolver.query( trackUri, new String[] { Tracks._ID, Tracks.NAME, Tracks.CREATION_TIME }, null, null, null );
         if( trackCursor.moveToFirst() )
         {
            serializer.text( "\n" );
            serializer.startTag( "", "Style" );
            serializer.attribute( null, "id", "lineStyle" );
            serializer.startTag( "", "LineStyle" );
            serializer.text( "\n" );
            serializer.startTag( "", "color" );
            serializer.text( "99ffac59" );
            serializer.endTag( "", "color" );
            serializer.text( "\n" );
            serializer.startTag( "", "width" );
            serializer.text( "6" );
            serializer.endTag( "", "width" );
            serializer.text( "\n" );
            serializer.endTag( "", "LineStyle" );
            serializer.text( "\n" );
            serializer.endTag( "", "Style" );
            serializer.text( "\n" );
            serializer.startTag( "", "Folder" );
            name = trackCursor.getString( 1 );
            serializer.text( "\n" );
            serializer.startTag( "", "name" );
            serializer.text(  name );
            serializer.endTag( "", "name" );
            serializer.text( "\n" );
            serializer.startTag( "", "open" );
            serializer.text( "1" );
            serializer.endTag( "", "open" );
            serializer.text( "\n" );
            serializer.startTag( "", "TimeStamp" );
            serializer.text( "\n" );
//            serializer.startTag( "", "when" );
//            Date time = new Date( trackCursor.getLong( 2 ) );
//            DateFormat formater = new SimpleDateFormat( DATETIME );
//            serializer.text( formater.format( time ) );
//            serializer.endTag( "", "when" );
            serializer.text( "\n" );
            serializer.endTag( "", "TimeStamp" );
            
            serializeSegments( serializer, Uri.withAppendedPath( trackUri, "segments" ) );
            

            serializer.text( "\n" );
            serializer.endTag( "", "Folder" );
         }
      }
      finally
      {
         if( trackCursor != null )
         {
            trackCursor.close();
         }
      }
      return name;
   }
   
   /**
    * <pre>
    * &lt;Folder>
    *    &lt;Placemark>
    *      serializeSegmentToTimespan()
    *      &lt;LineString>
    *         serializeWaypoints()
    *      &lt;/LineString>
    *    &lt;/Placemark>
    *    &lt;Placemark/>
    *    &lt;Placemark/>
    * &lt;/Folder>
    * </pre>
    * 
    * @param serializer
    * @param segments
    * @throws IOException
    */
   private void serializeSegments( XmlSerializer serializer, Uri segments ) throws IOException
   {
      Cursor segmentCursor = null;
      ContentResolver resolver = mContext.getContentResolver();
      try
      {
         segmentCursor = resolver.query( segments, new String[] { Segments._ID }, null, null, null );
         if( segmentCursor.moveToFirst() )
         {
            if( mProgressListener != null )
            {
               mProgressListener.updateNotification( getProgress(), getGoal() );
            }
            do
            {
               Uri waypoints = Uri.withAppendedPath( segments, "/" + segmentCursor.getLong( 0 ) + "/waypoints" );
               serializer.text( "\n" );
               serializer.startTag( "", "Folder" );
               serializer.text( "\n" );
               serializer.startTag( "", "name" );
               serializer.text(  String.format("Segment %d", 1+segmentCursor.getPosition()) );
               serializer.endTag( "", "name" );
               serializer.text( "\n" );
               serializer.startTag( "", "open" );
               serializer.text( "1" );
               serializer.endTag( "", "open" );
               
               /* Single <TimeSpan/> element */
               serializeSegmentToTimespan( serializer, waypoints );

               serializer.text( "\n" );
               serializer.startTag( "", "Placemark" );  
               serializer.text( "\n" );
               serializer.startTag( "", "name" );
               serializer.text( "Path" );
               serializer.endTag( "", "name" );
               serializer.text( "\n" );
               serializer.startTag( "", "styleUrl" );
               serializer.text( "#lineStyle" );
               serializer.endTag( "", "styleUrl" );
               serializer.text( "\n" );
               serializer.startTag( "", "LineString" );
               serializer.text( "\n" );
               serializer.startTag( "", "tessellate" );
               serializer.text( "0" );
               serializer.endTag( "", "tessellate" );
               serializer.text( "\n" );
               serializer.startTag( "", "altitudeMode" );
               serializer.text( "absolute" );
               serializer.endTag( "", "altitudeMode" );
               
               /* Single <coordinates/> element */
               serializeWaypoints( serializer, waypoints );
               
               serializer.text( "\n" );
               serializer.endTag( "", "LineString" );
               serializer.text( "\n" );
               serializer.endTag( "", "Placemark" );
               
               serializeWaypointDescription( serializer, Uri.withAppendedPath( segments, "/" + segmentCursor.getLong( 0 ) + "/media" ) );
               
               serializer.text( "\n" );
               serializer.endTag( "", "Folder" );
               

            }
            while (segmentCursor.moveToNext());
   
         }
      }
      finally
      {
         if( segmentCursor != null )
         {
            segmentCursor.close();
         }
      }
   }
   
   /**
    * &lt;TimeSpan>&lt;begin>...&lt;/begin>&lt;end>...&lt;/end>&lt;/TimeSpan>
    * 
    * @param serializer
    * @param waypoints
    * @throws IOException
    */
   private void serializeSegmentToTimespan( XmlSerializer serializer, Uri waypoints ) throws IOException
   {
      Cursor waypointsCursor = null;
      Date segmentStartTime = null ;
      Date segmentEndTime = null;
      ContentResolver resolver = mContext.getContentResolver();
      try
      {
         waypointsCursor = resolver.query( waypoints, new String[] { Waypoints.TIME }, null, null, null );

         if( waypointsCursor.moveToFirst() )
         {
            segmentStartTime = new Date( waypointsCursor.getLong( 0 ) );
         }
         if( waypointsCursor.moveToLast() )
         {
            segmentEndTime = new Date( waypointsCursor.getLong( 0 ) );
         }
      }
      finally
      {
         if( waypointsCursor != null )
         {
            waypointsCursor.close();
         }
      }
      
      DateFormat formater = new SimpleDateFormat( DATETIME );
      serializer.text( "\n" );
      serializer.startTag( "", "TimeSpan" );
      serializer.text( "\n" );
      serializer.startTag( "", "begin" );         
      serializer.text( formater.format( segmentStartTime ) );
      serializer.endTag( "", "begin" );
      serializer.text( "\n" );
      serializer.startTag( "", "end" );
      serializer.text( formater.format( segmentEndTime ) );
      serializer.endTag( "", "end" );
      serializer.text( "\n" );
      serializer.endTag( "", "TimeSpan" );
      
   }
   
   /**
    * &lt;coordinates>...&lt;/coordinates> 
    * 
    * @param serializer
    * @param waypoints
    * @throws IOException
    */
   private void serializeWaypoints( XmlSerializer serializer, Uri waypoints ) throws IOException
   {
      Cursor waypointsCursor = null;
      ContentResolver resolver = mContext.getContentResolver();
      try
      {
         waypointsCursor = resolver.query( waypoints, new String[] { Waypoints.LONGITUDE, Waypoints.LATITUDE, Waypoints.ALTITUDE }, null, null, null );
         if( waypointsCursor.moveToFirst() )
         {
            
            increaseGoal( waypointsCursor.getCount() );
            serializer.text( "\n" );
            serializer.startTag( "", "coordinates" );
            do
            {
               increaseProgress( 1 );
               if( mProgressListener != null )
               {
                  mProgressListener.updateNotification(getProgress(), getGoal());
               }
               // Single Coordinate tuple
               serializeCoordinates( serializer, waypointsCursor );
               serializer.text( " " );
            }
            while (waypointsCursor.moveToNext());
            
            serializer.endTag( "", "coordinates" );
         }
      }
      finally
      {
         if( waypointsCursor != null )
         {
            waypointsCursor.close();
         }
      }
   
   }

   /**
    * lon,lat,alt tuple without trailing spaces
    * 
    * @param serializer
    * @param waypointsCursor
    * @throws IOException
    */
   private void serializeCoordinates( XmlSerializer serializer, Cursor waypointsCursor ) throws IOException
   {
      serializer.text( Double.toString( waypointsCursor.getDouble( 0 ) ) );
      serializer.text( "," );
      serializer.text( Double.toString( waypointsCursor.getDouble( 1 ) ) );
      serializer.text( "," );
      serializer.text( Double.toString( waypointsCursor.getDouble( 2 ) ) );
   }
   
   private void serializeWaypointDescription( XmlSerializer serializer, Uri media ) throws IOException
   {
      String mediaPathPrefix = Environment.getExternalStorageDirectory().getAbsolutePath() + Constants.EXTERNAL_DIR;
      Cursor mediaCursor = null;
      ContentResolver resolver = mContext.getContentResolver();
      try
      {
         mediaCursor = resolver.query( media, new String[] { Media.URI, Media.TRACK, Media.SEGMENT, Media.WAYPOINT }, null, null, null );
         if( mediaCursor.moveToFirst() )
         {
            do
            {
               Uri mediaUri = Uri.parse( mediaCursor.getString( 0 ) );
               if( mediaUri.getScheme().equals( "file" ) )
               {
                  if( mediaUri.getLastPathSegment().endsWith( "3gp" ) )
                  {
//                     serializer.text( "\n" );
//                     serializer.startTag( "", "link" );
//                     serializer.attribute( null, "href", includeMediaFile( mediaPathPrefix + mediaUri.getLastPathSegment() ) );
//                     serializer.startTag( "", "text" );
//                     serializer.text( mediaUri.getLastPathSegment() );
//                     serializer.endTag( "", "text" );
//                     serializer.endTag( "", "link" );
                  }
                  else if( mediaUri.getLastPathSegment().endsWith( "jpg" ) )
                  {
                     serializer.text( "\n" );
                     serializer.startTag( "", "PhotoOverlay" );
                     serializer.text( "\n" );
                     serializer.startTag( "", "name" );
                     serializer.text( mediaUri.getLastPathSegment() );
                     serializer.endTag( "", "name" );
                     serializer.text( "\n" );
                     serializer.startTag( "", "Icon" );
                     serializer.text( "\n" );
                     serializer.startTag( "", "href" );
                     serializer.text( includeMediaFile( mediaPathPrefix + mediaUri.getLastPathSegment() ) );  
                     serializer.endTag( "", "href" );
                     serializer.text( "\n" );
                     serializer.endTag( "", "Icon" );
                     
                     Uri singleWaypointUri = Uri.withAppendedPath( Tracks.CONTENT_URI, mediaCursor.getLong(1)+"/segments/"+mediaCursor.getLong(2)+"/waypoints/"+mediaCursor.getLong(3) );
                     serializeMediaPoint( serializer, singleWaypointUri );

                     serializer.text( "\n" );
                     serializer.endTag( "", "PhotoOverlay" );
                  }
                  else if( mediaUri.getLastPathSegment().endsWith( "txt" ) )
                  {
//                     serializer.text( "\n" );
//                     serializer.startTag( "", "desc" );
//                     BufferedReader buf = new BufferedReader( new FileReader( mediaUri.getEncodedPath() ) );
//                     String line;
//                     while( ( line = buf.readLine() ) != null )
//                     {
//                        serializer.text( line );
//                        serializer.text( "\n" );
//                     }
//                     serializer.endTag( "", "desc" );
                  }
               }
               else if( mediaUri.getScheme().equals( "content" ) )
               {
                  if( mediaUri.getAuthority().equals( GPStracking.AUTHORITY + ".string" ) )
                  {
//                     serializer.text( "\n" );
//                     serializer.startTag( "", "name" );
//                     serializer.text( mediaUri.getLastPathSegment() );
//                     serializer.endTag( "", "name" );
                  }
                  else if( mediaUri.getAuthority().equals( "media" ) )
                  {
//                     Cursor mediaItemCursor = null;
//                     try
//                     {
//                        mediaItemCursor = resolver.query( mediaUri, new String[] { MediaColumns.DATA, MediaColumns.DISPLAY_NAME }, null, null, null );
//                        if( mediaItemCursor.moveToFirst() )
//                        {
//                           serializer.text( "\n" );
//                           serializer.startTag( "", "link" );
//                           serializer.attribute( null, "href", includeMediaFile( mediaItemCursor.getString( 0 ) ) );
//                           serializer.startTag( "", "text" );
//                           serializer.text( mediaItemCursor.getString( 1 ) );
//                           serializer.endTag( "", "text" );
//                           serializer.endTag( "", "link" );
//                        }
//                     }
//                     finally
//                     {
//                        if( mediaItemCursor != null )
//                        {
//                           mediaItemCursor.close();
//                        }
//                     }
                  }
               }
            }
            while( mediaCursor.moveToNext() );
         }
      }
      finally
      {
         if( mediaCursor != null )
         {
            mediaCursor.close();
         }
      }
   }

   private void serializeMediaPoint( XmlSerializer serializer, Uri singleWaypointUri ) throws IllegalArgumentException, IllegalStateException, IOException
   {
      Cursor waypointsCursor = null;
      ContentResolver resolver = mContext.getContentResolver();
      try
      {
         waypointsCursor = resolver.query( singleWaypointUri, new String[] { Waypoints.LONGITUDE, Waypoints.LATITUDE, Waypoints.ALTITUDE }, null, null, null );
         if( waypointsCursor.moveToFirst() )
         {
            serializer.text( "\n" );
            serializer.startTag( "", "Point" );
            serializer.text( "\n" );
            serializer.startTag( "", "coordinates" );
            
            serializeCoordinates( serializer, waypointsCursor );
            
            serializer.endTag( "", "coordinates" );
            serializer.text( "\n" );
            serializer.endTag( "", "Point" );
            serializer.text( "\n" );
            serializer.startTag( "", "shape" );            
            serializer.text( "rectangle" );
            serializer.endTag( "", "shape" );
         }
      }
      finally
      {
         if( waypointsCursor != null )
         {
            waypointsCursor.close();
         }
      }
   }
}