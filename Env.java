package example;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.logging.Logger;

import jason.asSyntax.Literal;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Structure;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

public class Env extends Environment {
    public static final int N = 9; // Grid size
    public static final int OBSTACLE = 32; // Obstacle mask
    public static final int TARGET = 16; // Target mask

    private Logger logger = Logger.getLogger("projectAgent." + Env.class.getName());

    private GridModel model;
    private GridView view;

    @Override
    public void init(String[] args) {
        model = new GridModel();
        view = new GridView(model);
        model.setView(view);
        updatePercepts();
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        try {
            if (action.getFunctor().equals("moveToTarget")) {
                int agentId = getAgentId(ag);

                // Get target coordinates
                int targetX = (int) ((NumberTerm) action.getTerm(0)).solve();
                int targetY = (int) ((NumberTerm) action.getTerm(1)).solve();

                // Get agent position
                Location agentLoc = model.getAgPos(agentId);

                // Calculate path using A* algorithm
                List<int[]> path = model.findPath(agentLoc.x, agentLoc.y, targetX, targetY);
                if (path != null && !path.isEmpty()) {
                    for (int[] step : path) {
                        if (model.getRemainingSteps(agentId) > 0) {
                            model.moveAgent(agentId, step[0], step[1]);
                            model.decrementSteps(agentId);
                            Thread.sleep(200); // Simulate agent movement

                            // Check if the agent collects a target
                            if (model.collectTarget(step[0], step[1])) {
                                logger.info("Agent " + agentId + " collected a target at " + step[0] + ", " + step[1]);
                            }
                        } else {
                            logger.warning("Agent " + agentId + " has no steps remaining.");
                            break;
                        }
                    }
                }
                logger.info("Reward: " + model.getAgentReward(0) );

                updatePercepts();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private int getAgentId(String agName) {
        // Convert agent name (e.g., "agent0") to ID
        return Integer.parseInt(agName.replace("agent", ""));
    }

    @Override
    public List<Literal> getPercepts(String agName) {
        int agentId = getAgentId(agName);
        List<Literal> percepts = new ArrayList<>();

        Location agentLocation = model.getAgPos(agentId);
        percepts.add(Literal.parseLiteral("pos(" + agentLocation.x + "," + agentLocation.y + ")"));
        percepts.add(Literal.parseLiteral("remainingSteps(" + model.getRemainingSteps(agentId) + ")"));
        percepts.add(Literal.parseLiteral("currentReward(" + model.getAgentReward(agentId) + ")"));

        for (Location obs : model.getObstacles()) {
            percepts.add(Literal.parseLiteral("obstacle(" + obs.x + "," + obs.y + ")"));
        }

        for (Target target : model.getTargets()) {
            percepts.add(Literal.parseLiteral(
                "target(" + target.getX() + "," + target.getY() + "," + target.getReward() + ")"
            ));
        }

        return percepts;
    }

    private void updatePercepts() {
        clearPercepts();
        for (int i = 0; i < model.getNbOfAgs(); i++) {
            String agName = "agent" + i;
            List<Literal> percepts = getPercepts(agName);
            for (Literal percept : percepts) {
                addPercept(agName, percept);
            }
        }
    }

    public class GridModel extends GridWorldModel {
        private final List<Target> targets = new ArrayList<>();
        private final Random random = new Random();
        private final int[] remainingSteps;
        private final double[] agentRewards;

        public GridModel() {
            super(N, N, 1); 
            remainingSteps = new int[]{31};
            agentRewards = new double[]{-0.31};
            try {
                setAgPos(0, random.nextInt(N), random.nextInt(N));
            
            } catch (Exception e) {
                e.printStackTrace();
            }
            placeObstacles();
            placeTargets();
        }

        private void placeObstacles() {
            for (int i = 0; i < 4; i++) {
                int x, y;
                do {
                    x = random.nextInt(N);
                    y = random.nextInt(N);
                } while (hasObject(OBSTACLE, x, y) || isAgentAtLocation(x, y));
                add(OBSTACLE, x, y);
            }
        }

        public void placeTargets() {
            targets.clear();

            double[] rewards = {0.8, 0.3, 0.5, 0.2};
            Color[] colors = {Color.GREEN, Color.YELLOW, Color.BLUE, Color.MAGENTA};

            List<Location> availableLocations = new ArrayList<>();
            for (int x = 0; x < N; x++) {
                for (int y = 0; y < N; y++) {
                    if (!hasObject(OBSTACLE, x, y) && !isAgentAtLocation(x, y)) {
                        availableLocations.add(new Location(x, y));
                    }
                }
            }

            for (int i = 0; i < 4 && !availableLocations.isEmpty(); i++) {
                Location loc = availableLocations.remove(random.nextInt(availableLocations.size()));
                Target target = new Target(loc.x, loc.y, rewards[i], colors[i]);
                targets.add(target);
                add(TARGET, loc);
            }
        }

        public boolean isAgentAtLocation(int x, int y) {
            for (int i = 0; i < getNbOfAgs(); i++) {
                Location agentLoc = getAgPos(i);
                if (agentLoc.x == x && agentLoc.y == y) {
                    return true;
                }
            }
            return false;
        }

        public int getAgentAtLocation(int x, int y) {
            for (int i = 0; i < getNbOfAgs(); i++) {
                Location agentLoc = getAgPos(i);
                if (agentLoc.x == x && agentLoc.y == y) {
                    return i;
                }
            }
            return -1; // No agent at this location
        }

        public Target getTargetAt(int x, int y) {
            for (Target target : targets) {
                if (target.getX() == x && target.getY() == y) {
                    return target;
                }
            }
            return null;
        }

        public boolean collectTarget(int x, int y) {
            Target target = getTargetAt(x, y);
            if (target != null) {
                targets.remove(target);
                remove(TARGET, x, y);

                // Add target reward to the collecting agent's reward
                int agentId = getAgentAtLocation(x, y);
                if (agentId != -1) {
                    agentRewards[agentId] += target.getReward();
                }

                // Regenerate a new target at a random location
                List<Location> availableLocations = new ArrayList<>();
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        if (!hasObject(OBSTACLE, i, j) && !isAgentAtLocation(i, j) && getTargetAt(i, j) == null) {
                            availableLocations.add(new Location(i, j));
                        }
                    }
                }

                if (!availableLocations.isEmpty()) {
                    Location newLoc = availableLocations.get(random.nextInt(availableLocations.size()));
                    Target newTarget = new Target(newLoc.x, newLoc.y, target.getReward(), target.getColor());
                    targets.add(newTarget);
                    add(TARGET, newLoc);
                }

                return true;
            }
            return false;
        }

        public int getRemainingSteps(int agentId) {
            return remainingSteps[agentId];
        }

        public void decrementSteps(int agentId) {
            if (remainingSteps[agentId] > 0) {
                remainingSteps[agentId]--;
            }
        }

        public double getAgentReward(int agentId) {
            return agentRewards[agentId];
        }

        public void decreaseReward(int agentId, double amount) {
            agentRewards[agentId] -= amount;
        }

        public void moveAgent(int id, int x, int y) {
            try {
                Location old = getAgPos(id);
                setAgPos(id, x, y);
                logger.info("Agent " + id + " moved from " + old + " to " + getAgPos(id));
            } catch (Exception e) {
                logger.severe("Move failed for agent " + id + ": " + e.getMessage());
            }
        }

        public List<int[]> findPath(int startX, int startY, int goalX, int goalY) {
            PriorityQueue<Node> openList = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
            List<Node> closedList = new ArrayList<>();

            Node startNode = new Node(null, startX, startY, 0, heuristic(startX, startY, goalX, goalY));
            openList.add(startNode);

            while (!openList.isEmpty()) {
                Node currentNode = openList.poll();
                closedList.add(currentNode);

                if (currentNode.x == goalX && currentNode.y == goalY) {
                    return reconstructPath(currentNode);
                }

                for (int[] neighbor : getNeighbors(currentNode)) {
                    int nx = neighbor[0];
                    int ny = neighbor[1];

                    if (hasObject(OBSTACLE, nx, ny) || containsNode(closedList, nx, ny)) {
                        continue;
                    }

                    double tentativeG = currentNode.g + 1;

                    Node neighborNode = getNode(openList, nx, ny);
                    if (neighborNode == null) {
                        neighborNode = new Node(currentNode, nx, ny, tentativeG, heuristic(nx, ny, goalX, goalY));
                        openList.add(neighborNode);
                    } else if (tentativeG < neighborNode.g) {
                        openList.remove(neighborNode);
                        neighborNode.parent = currentNode;
                        neighborNode.g = tentativeG;
                        openList.add(neighborNode);
                    }
                }
            }

            return null;
        }

        private List<int[]> reconstructPath(Node node) {
            List<int[]> path = new ArrayList<>();
            while (node != null) {
                path.add(new int[]{node.x, node.y});
                node = node.parent;
            }
            Collections.reverse(path);
            return path;
        }

        private List<int[]> getNeighbors(Node node) {
            List<int[]> neighbors = new ArrayList<>();
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // 4-directional
            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                if (nx >= 0 && nx < N && ny >= 0 && ny < N) {
                    neighbors.add(new int[]{nx, ny});
                }
            }
            return neighbors;
        }

        private double heuristic(int x1, int y1, int x2, int y2) {
            return Math.abs(x1 - x2) + Math.abs(y1 - y2); // Manhattan distance
        }

        private boolean containsNode(List<Node> list, int x, int y) {
            return list.stream().anyMatch(n -> n.x == x && n.y == y);
        }

        private Node getNode(PriorityQueue<Node> list, int x, int y) {
            return list.stream().filter(n -> n.x == x && n.y == y).findFirst().orElse(null);
        }

        private class Node {
            Node parent;
            int x, y;
            double g, h, f;

            Node(Node parent, int x, int y, double g, double h) {
                this.parent = parent;
                this.x = x;
                this.y = y;
                this.g = g;
                this.h = h;
                this.f = g + h;
            }
        }

        public List<Location> getObstacles() {
            List<Location> obstacles = new ArrayList<>();
            for (int x = 0; x < N; x++) {
                for (int y = 0; y < N; y++) {
                    if (hasObject(OBSTACLE, x, y)) {
                        obstacles.add(new Location(x, y));
                    }
                }
            }
            return obstacles;
        }

        public List<Target> getTargets() {
            return targets;
        }
    }

    public class Target {
        private int x, y;
        private double reward;
        private Color color;

        public Target(int x, int y, double reward, Color color) {
            this.x = x;
            this.y = y;
            this.reward = reward;
            this.color = color;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public double getReward() {
            return reward;
        }

        public Color getColor() {
            return color;
        }
    }

    public class GridView extends GridWorldView {
        public GridView(GridModel model) {
            super(model, "Grid World", 600);
            defaultFont = new Font("Arial", Font.BOLD, 18);
            setVisible(true);
            repaint();
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            if (object == OBSTACLE) {
                g.setColor(Color.BLACK);
                super.drawObstacle(g, x, y);
            } else if (object == TARGET) {
                Target target = ((GridModel) model).getTargetAt(x, y);
                if (target != null) {
                    g.setColor(target.getColor());
                    g.fillRect(x * cellSizeW, y * cellSizeH, cellSizeW, cellSizeH);
                }
            }
        }

        @Override
        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            g.setColor(c);
            drawString(g, x, y, defaultFont, "A" + id);
        }
    }

    @Override
    public void stop() {
        super.stop();
    }
}