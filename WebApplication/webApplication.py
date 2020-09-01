from simulateWildfire import insert_fire, delete_fire, update_fires
from multiprocessing import Process
from flask import Flask, render_template, request
from datetime import datetime, timedelta
import sqlite3
import time

app = Flask(__name__)
app.secret_key = 'EW_Demo_08_2020'


# Separate process which updates the simulator every 10 seconds
def update_simulator():
    while True:
        update_fires()
        time.sleep(10)


# Start the Flask server on localhost
def start_server():
    app.run(debug=True, host='0.0.0.0', use_reloader=False)


# When client loads the web application, return contents of local database
@app.route('/')
def index():
    conn = sqlite3.connect('db/db.sqlite')
    cursor = conn.cursor()

    # Query the SQLite database and return rows as result
    cursor.execute("SELECT sid, ROUND(latitude, 5), ROUND(longitude, 5), strftime('%d-%m-%Y %H:%M', t_init), strftime('%d-%m-%Y %H:%M', t_updated), comments, active FROM wildfires")

    rows = cursor.fetchall()
    return render_template('index.html', rows=rows)


# When form is submitted, add the wildfire to the system
@app.route('/addWildfire', methods=['POST'])
def addWildfire():
    data = request.form

    # Parse the form data into timestamps and coordinates
    t_init = datetime.now() - timedelta(minutes=int(data['time']))
    lat = float(data['latitude'])
    lon = float(data['longitude'])
    comments = data['comments']

    # Insert the fire into the EW system and return updated rows
    rows = insert_fire(lat, lon, t_init, comments)

    return render_template('index.html', rows=rows)


# When delete operation is confirmed, delete wildfire from system
@app.route('/deleteWildfire', methods=['POST'])
def deleteWildfire():
    # Get the fire ID from the form
    sid = request.form['sid']

    # Delete the fire from the EW system and return updated rows
    rows = delete_fire(sid)
    return render_template('index.html', rows=rows)


# Activate wildfire such that the ForeFire simulator is running again
@app.route('/activateWildfire', methods=['POST'])
def activateWildfire():
    conn = sqlite3.connect('db/db.sqlite')
    cursor = conn.cursor()

    sid = request.form['sid']

    # Set fire as active
    query = """UPDATE wildfires SET active = 1 WHERE sid == (?)"""
    cursor.execute(query, (sid,))

    # Return updated rows of the local SQLite database
    cursor.execute("SELECT sid, ROUND(latitude, 5), ROUND(longitude, 5), strftime('%d-%m-%Y %H:%M', t_init), strftime('%d-%m-%Y %H:%M', t_updated), comments, active FROM wildfires")
    rows = cursor.fetchall()

    conn.commit()
    cursor.close()
    conn.close()

    print("Wildfire " + sid + " was re-activated.")

    return render_template('index.html', rows=rows)


# Deactivate wildfire such that the ForeFire simulator is terminated
@app.route('/deactivateWildfire', methods=['POST'])
def deactivateWildfire():
    conn = sqlite3.connect('db/db.sqlite')
    cursor = conn.cursor()

    sid = request.form['sid']

    # Set fire as inactive
    query = """UPDATE wildfires SET active = 0 WHERE sid == (?)"""
    cursor.execute(query, (sid,))

    # Return updated rows of the local SQLite database
    cursor.execute("SELECT sid, ROUND(latitude, 5), ROUND(longitude, 5), strftime('%d-%m-%Y %H:%M', t_init), strftime('%d-%m-%Y %H:%M', t_updated), comments, active FROM wildfires")
    rows = cursor.fetchall()

    conn.commit()
    cursor.close()
    conn.close()

    print("Wildfire " + sid + " was deactivated.")

    return render_template('index.html', rows=rows)


# Keep two processes running simultaneously
if __name__ == '__main__':
    # Run Flask server process
    p_server = Process(target=start_server)
    p_server.start()

    # Run simulator process which updates active fires in real-time
    p_sim = Process(target=update_simulator)
    p_sim.start()



