`datomic-graph-viz` is a tool for visualizing Datomic data with an interactive explorable graph. 

### Demo screencast
[![Demo](https://img.youtube.com/vi/ktcxJWeJhP8/0.jpg)](https://www.youtube.com/watch?v=ktcxJWeJhP8)


### Getting started 
#### With your own datomic database 
To run against your own datomic database, simply run 
```shell
bb start <your-datomic-connection-string>
```
Then open the printed link to get started.

Available options to `bb start` are: 
```
--port                 the port to the frontend is served on, default: 1234
--max-edges-per-level  the maximum number of edges fetched per level, default: 100
--conn-str             the datomic connection string to use
```


#### With the mbrainz sample database
To explore the mbrainz sample database yourself, some utilities are provided.
Run:
```shell
bb mbrainz-demo-transactor
```
When the transactor is running, run this in another terminal: 
```shell
bb mbrainz-demo
```
And you're off!
