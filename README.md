`datomic-graph-viz` is a tool for visualizing Datomic data with an interactive explorable graph. 

### Demo screencast
[![Demo](https://img.youtube.com/vi/ktcxJWeJhP8/0.jpg)](https://www.youtube.com/watch?v=ktcxJWeJhP8)


### Getting started 

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

### Usage 

#### Mouse actions 
You can drag nodes around, and grab the background to drag the whole graph around. 

Double clicking a node navigates to that node. This creates a new graph with that entity as the root node.

#### Form input 
The eid (or lookup-ref) input sets the root node of the graph. This is automatically set when navigating with double clicks. Leave it empty to get a random entity, which is what happens on the first page load. (The random selection excludes entities without any refs to or from it).

The ancestor and descendant inputs decide how many levels of nodes are fetched in their respective directions. Limit these to control which nodes you're interested in seeing. Note that ancestors of descendants aren't fetched, and neither are descendants of ancestors. Navigate to these nodes to see all their ancestors and descendants.

#### Visuals
Nodes are colored semi-randomly by hashing their set of attributes. That means that nodes with the same attributes have the same color. Note that similar colors does not mean that their attribute sets are similar. Likewise, completely different nodes can have very similar colors, and may even collide.    

