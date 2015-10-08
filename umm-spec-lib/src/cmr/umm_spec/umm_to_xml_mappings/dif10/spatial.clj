(ns cmr.umm-spec.umm-to-xml-mappings.dif10.spatial
  (:require [cmr.umm-spec.xml.gen :refer :all]))

;; TODO: We need to consolidate the SpatialCoverageTypeEnum between UMM JSON and DIF10
;; See CMR-1990
(def umm-spatial-type->dif10-spatial-type
  {"HORIZONTAL" "Horizontal"
   "VERTICAL" "Vertical"
   "ORBITAL" "Orbit"})

(defn- point-contents
  "Returns the inner lon/lat elements for a DIF Point element from a UMM PointType record."
  [point]
  (list [:Point_Longitude (:Longitude point)]
        [:Point_Latitude  (:Latitude  point)]))

(defn- point-element
  "Returns DIF Point element from a given UMM PointType record."
  [point]
  [:Point
   (point-contents point)])

(defn- bounding-rect-element
  "Returns DIF 10 Bounding_Rectangle element from a UMM BoundingRectangleType record."
  [rect]
  [:Bounding_Rectangle
   [:Center_Point (point-contents (:CenterPoint rect))]
   [:Southernmost_Latitude (:SouthBoundingCoordinate rect)]
   [:Northernmost_Latitude (:NorthBoundingCoordinate rect)]
   [:Westernmost_Longitude (:WestBoundingCoordinate rect)]
   [:Easternmost_Longitude (:EastBoundingCoordinate rect)]])

(defn- polygon-element
  "Returns DIF 10 Polygon element from UMM GPolygonType record."
  [poly]
  [:Polygon
   [:Boundary
    (map point-element (-> poly :Boundary :Points))]
   [:Exclusive_Zone
    (for [b (-> poly :ExclusiveZone :Boundaries)]
      [:Boundary
       (map point-element (:Points b))])]
   [:Center_Point
    (point-contents (:CenterPoint poly))]])

(defn- line-element
  "Returns DIF 10 Line element from given UMM LineType record."
  [line]
  [:Line
   (map point-element (:Points line))
   ;; Yes, it's CenterPoint here, and Center_Point everywhere else.
   [:CenterPoint (point-contents (:CenterPoint line))]])

(defn spatial-element
  "Returns DIF10 Spatial_Coverage element from given UMM-C record."
  [c]
  (let [sp (:SpatialExtent c)]
    [:Spatial_Coverage
     [:Spatial_Coverage_Type (umm-spatial-type->dif10-spatial-type (:SpatialCoverageType sp))]
     [:Granule_Spatial_Representation (:GranuleSpatialRepresentation sp)]
     [:Zone_Identifier (-> sp :HorizontalSpatialDomain :ZoneIdentifier)]
     (let [geom (-> sp :HorizontalSpatialDomain :Geometry)]
       [:Geometry
        [:Coordinate_System (:CoordinateSystem geom)]
        ;; DIF 10 only supports one of each type of geometry, so we just use the first one.
        (first
          (concat
            ;; From most-specific to least specific. This is arbitrary.
            (map polygon-element (:GPolygons geom))
            (map bounding-rect-element (:BoundingRectangles geom))
            (map line-element (:Lines geom))
            (map point-element (:Points geom))))])
     (let [o (:OrbitParameters sp)]
       [:Orbit_Parameters
        [:Swath_Width (:SwathWidth o)]
        [:Period (:Period o)]
        [:Inclination_Angle (:InclinationAngle o)]
        [:Number_Of_Orbits (:NumberOfOrbits o)]
        [:Start_Circular_Latitude (:StartCircularLatitude o)]])
     (for [vert (:VerticalSpatialDomains sp)]
       [:Vertical_Spatial_Info
        (elements-from vert :Type :Value)])]))