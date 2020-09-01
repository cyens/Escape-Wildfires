//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// Tooltips
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
$(document).ready(function () {
    // Activate tooltip
    $('[data-toggle="tooltip"]').tooltip();

    // Select/Deselect checkboxes
    var checkbox = $('table tbody input[type="checkbox"]');
    $("#selectAll").click(function () {
        if (this.checked) {
            checkbox.each(function () {
                this.checked = true;
            });
        } else {
            checkbox.each(function () {
                this.checked = false;
            });
        }
    });
    checkbox.click(function () {
        if (!this.checked) {
            $("#selectAll").prop("checked", false);
        }
    });
});

//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// Time selector
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
function computeDate(delta) {
    var today = new Date(Date.now() - delta * 1000 * 60);
    var date = today.getFullYear() + '-' + (today.getMonth() + 1) + '-' + today.getDate();
    var time = today.getHours() + ":" + today.getMinutes() + ":" + today.getSeconds();
    return date + ' ' + time;
}

var $ignitionTime = $("#ignitionTimeSelector");
$ignitionTime.inputSpinner();

$ignitionTime.on("change", function (event) {
    document.getElementById("ignitionTimeDisplay").innerHTML = computeDate($ignitionTime.val());
});


//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// Mapping and location selection
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
var platform = new H.service.Platform({
  'apikey': 'st_InqC3mDBLQw4q75F6Hbm4cw5hiPuCTk_hbkMif_w'
});

// Obtain the default map types from the platform object:
var defaultLayers = platform.createDefaultLayers();

// Instantiate (and display) a map object:
var selectionMap = new H.Map(
    document.getElementById('mapContainer'),
    defaultLayers.vector.normal.map,
    {
      zoom: 7,
      center: { lat: 42.110795, lng: 9.143008 }
    });

var displayMap = new H.Map(
    document.getElementById('locationContainer'),
    defaultLayers.vector.normal.map,
    {
      zoom: 7,
      center: { lat: 42.110795, lng: 9.143008 }
    });

function addDraggableMarker(map, behavior) {
    var marker = new H.map.Marker({lat: 42.110795, lng: 9.143008}, {
        // mark the object as volatile for the smooth dragging
        volatility: true
    });
    // Ensure that the marker can receive drag events
    marker.draggable = true;
    map.addObject(marker);

    // disable the default draggability of the underlying map
    // and calculate the offset between mouse and target's position
    // when starting to drag a marker object:
    map.addEventListener('dragstart', function (ev) {
        var target = ev.target,
            pointer = ev.currentPointer;
        if (target instanceof H.map.Marker) {
            var targetPosition = map.geoToScreen(target.getGeometry());
            target['offset'] = new H.math.Point(pointer.viewportX - targetPosition.x, pointer.viewportY - targetPosition.y);
            behavior.disable();
        }
    }, false);


    // re-enable the default draggability of the underlying map
    // when dragging has completed
    map.addEventListener('dragend', function (ev) {
        var target = ev.target;
        if (target instanceof H.map.Marker) {
            behavior.enable();
        }
        document.getElementById("ignitionLat").value = target['b']['lat'];
        document.getElementById("ignitionLon").value = target['b']['lng'];
    }, false);

    // Listen to the drag event and move the position of the marker
    // as necessary
    map.addEventListener('drag', function (ev) {
        var target = ev.target,
            pointer = ev.currentPointer;
        if (target instanceof H.map.Marker) {
            target.setGeometry(map.screenToGeo(pointer.viewportX - target['offset'].x, pointer.viewportY - target['offset'].y));
        }
    }, false);
}

// Create the default UI:
var ui = H.ui.UI.createDefault(selectionMap, defaultLayers);

// Enable the event system on the map instance:
var mapEvents = new H.mapevents.MapEvents(selectionMap);

// Instantiate the default behavior, providing the mapEvents object:
var behavior = new H.mapevents.Behavior(mapEvents);

// Enable the draggable marker
addDraggableMarker(selectionMap, behavior);

$("#addWildfireModal").on('shown.bs.modal', function() {
  selectionMap.getViewPort().resize();
});

$("#showLocationModal").on('shown.bs.modal', function() {
  displayMap.getViewPort().resize();
});


//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
// Edit/delete modals
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
$('#activateWildfireModal').on('show.bs.modal', function (event) {
    var button = $(event.relatedTarget) // Button that triggered the modal
    var sid = button.data('sid') // Extract info from data-* attributes
    // If necessary, you could initiate an AJAX request here (and then do the updating in a callback).
    // Update the modal's content. We'll use jQuery here, but you could use a data binding library or other methods instead.

    $(this).find('.modal-body input').val(sid)
});

$('#deactivateWildfireModal').on('show.bs.modal', function (event) {
    var button = $(event.relatedTarget) // Button that triggered the modal
    var sid = button.data('sid') // Extract info from data-* attributes
    // If necessary, you could initiate an AJAX request here (and then do the updating in a callback).
    // Update the modal's content. We'll use jQuery here, but you could use a data binding library or other methods instead.

    $(this).find('.modal-body input').val(sid)
});

$('#deleteWildfireModal').on('show.bs.modal', function (event) {
    var button = $(event.relatedTarget) // Button that triggered the modal
    var sid = button.data('sid') // Extract info from data-* attributes
    // If necessary, you could initiate an AJAX request here (and then do the updating in a callback).
    // Update the modal's content. We'll use jQuery here, but you could use a data binding library or other methods instead.

    $(this).find('.modal-body input').val(sid)
});

$('#showLocationModal').on('show.bs.modal', function (event) {
    displayMap.removeObjects(displayMap.getObjects())

    var button = $(event.relatedTarget) // Button that triggered the modal
    var lat = button.data('lat')
    var lon = button.data('lon')

    var marker = new H.map.Marker({lat: lat, lng: lon});
    displayMap.addObject(marker);
});
