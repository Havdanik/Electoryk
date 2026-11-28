package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import com.elektoryk.scan.model.ReviewIssue;
import com.elektoryk.scan.model.RoomBounds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class InteriorObjectDetector {
    private static class Cell {
        int x,y,z;
        int count;
        double confidenceSum;
        double minX=Double.POSITIVE_INFINITY, maxX=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY, maxY=Double.NEGATIVE_INFINITY;
        double minZ=Double.POSITIVE_INFINITY, maxZ=Double.NEGATIVE_INFINITY;
        // Component traversal state lives on the voxel itself. Cells are rebuilt for every
        // detect() pass, so a separate HashSet<Long> only duplicated the map keys and boxed
        // one Long per occupied voxel.
        boolean visited;
    }

    /** Primitive long -> Cell table used only during one detection pass. Avoids HashMap nodes and
     * Long boxing for every occupied 3D voxel. */
    private static final class LongCellMap {
        long[] keys=new long[256];
        Cell[] values=new Cell[256];
        boolean[] used=new boolean[256];
        int size;
        Cell get(long key){
            int m=keys.length-1, i=mix(key)&m;
            while(used[i]){ if(keys[i]==key)return values[i]; i=(i+1)&m; }
            return null;
        }
        void put(long key,Cell value){
            if((size+1)*10>=keys.length*7)rehash(keys.length<<1);
            int m=keys.length-1,i=mix(key)&m;
            while(used[i]){ if(keys[i]==key){values[i]=value;return;} i=(i+1)&m; }
            used[i]=true;keys[i]=key;values[i]=value;size++;
        }
        private void rehash(int cap){
            long[] ok=keys;Cell[] ov=values;boolean[] ou=used;
            keys=new long[cap];values=new Cell[cap];used=new boolean[cap];size=0;
            for(int i=0;i<ok.length;i++)if(ou[i])put(ok[i],ov[i]);
        }
        private static int mix(long x){
            x^=x>>>33;x*=0xff51afd7ed558ccdl;x^=x>>>33;x*=0xc4ceb9fe1a85ec53l;x^=x>>>33;
            return (int)x;
        }
    }

    private final double cellM;

    public InteriorObjectDetector(double cellM) {
        this.cellM = Math.max(0.10, cellM);
    }

    public List<ReviewIssue> detect(List<CloudPoint> points, RoomBounds b) {
        List<ReviewIssue> out = new ArrayList<>();
        if (points == null || b == null || !b.isUsable()) return out;

        LongCellMap cells = new LongCellMap();
        // Iteration is kept separately because the primitive map intentionally exposes no boxed
        // entrySet. There is exactly one Cell reference per occupied voxel in this list.
        List<Cell> occupied = new ArrayList<>();
        double wallGap = 0.12;
        double floorGap = 0.08;
        double ceilingGap = 0.10;

        for (CloudPoint p : points) {
            // Defensive sensor boundary: never let NaN/Infinity enter voxel keys or component
            // bounds. A non-finite min/max would later make ReviewIssue JSON non-serializable.
            if (p == null || !Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) continue;
            if (p.x <= b.minX+wallGap || p.x >= b.maxX-wallGap
                    || p.z <= b.minZ+wallGap || p.z >= b.maxZ-wallGap
                    || p.y <= b.floorY+floorGap || p.y >= b.ceilingY-ceilingGap) {
                continue;
            }

            int x = (int)Math.floor((p.x-b.minX)/cellM);
            int y = (int)Math.floor((p.y-b.floorY)/cellM);
            int z = (int)Math.floor((p.z-b.minZ)/cellM);
            long k = pack(x,y,z);
            Cell c = cells.get(k);
            if (c == null) {
                c = new Cell(); c.x=x; c.y=y; c.z=z;
                cells.put(k,c);
                occupied.add(c);
            }
            c.count++;
            // Preserve measurement quality in the occupancy evidence. The fused cloud already
            // exposes confidence, but object clustering previously treated a barely-confirmed
            // voxel exactly like a high-quality multi-view voxel. That let low-confidence depth
            // haze form valid two-hit bridge cells even after the speckle guard.
            c.confidenceSum += Float.isFinite(p.confidence)
                    ? Math.max(0.0, Math.min(1.0, p.confidence)) : 0.0;
            c.minX=Math.min(c.minX,p.x); c.maxX=Math.max(c.maxX,p.x);
            c.minY=Math.min(c.minY,p.y); c.maxY=Math.max(c.maxY,p.y);
            c.minZ=Math.min(c.minZ,p.z); c.maxZ=Math.max(c.maxZ,p.z);
        }

        // No visited HashSet: Cell.visited is authoritative for this pass. This halves the
        // boxed-key bookkeeping in dense interiors and avoids a second hash lookup per voxel.
        ArrayDeque<Cell> q = new ArrayDeque<>();

        for (Cell seed : occupied) {
            if (seed.visited) continue;
            // Mark sparse seeds as consumed too; otherwise they remain logically unvisited and
            // make future maintenance/error-prone traversals revisit them.
            if (!reliable(seed)) { seed.visited=true; continue; }

            q.clear();
            q.add(seed);
            seed.visited=true;

            int total = 0;
            int cellCount = 0;
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
            double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;

            while (!q.isEmpty()) {
                Cell c = q.removeFirst();
                cellCount++;
                total += c.count;
                minX=Math.min(minX,c.minX); maxX=Math.max(maxX,c.maxX);
                minY=Math.min(minY,c.minY); maxY=Math.max(maxY,c.maxY);
                minZ=Math.min(minZ,c.minZ); maxZ=Math.max(maxZ,c.maxZ);

                // Spell out six neighbours instead of allocating/iterating int[][] direction
                // arrays. enqueue() performs one map lookup and marks before queueing, so a cell
                // can never be queued twice through two adjacent parents.
                enqueue(cells,q,c.x+1,c.y,c.z);
                enqueue(cells,q,c.x-1,c.y,c.z);
                enqueue(cells,q,c.x,c.y+1,c.z);
                enqueue(cells,q,c.x,c.y-1,c.z);
                enqueue(cells,q,c.x,c.y,c.z+1);
                enqueue(cells,q,c.x,c.y,c.z-1);
            }

            double w=maxX-minX, h=maxY-minY, dep=maxZ-minZ;
            if (total < 18 || cellCount < 3) continue;
            if (w < 0.10 && h < 0.10 && dep < 0.10) continue;
            if (w > b.widthM()*0.85 && dep > b.lengthM()*0.85) continue;

            ReviewIssue r = new ReviewIssue();
            r.minX=minX; r.maxX=maxX;
            r.minY=minY; r.maxY=maxY;
            r.minZ=minZ; r.maxZ=maxZ;
            r.supportPoints=total;

            double nearWall = Math.min(
                    Math.min(minX-b.minX, b.maxX-maxX),
                    Math.min(minZ-b.minZ, b.maxZ-maxZ));
            boolean touchesFloor = minY - b.floorY < 0.22;

            if (nearWall < 0.45 && Math.max(w,dep) < 1.8 && h < 2.7) {
                r.suggestedType = ReviewIssue.SuggestedType.TEMPORARY_OBJECT;
                r.confidence = touchesFloor ? 0.74 : 0.62;
                r.reason = "Локальний об'єм виступає перед стабільною площиною стіни; "
                        + "схоже на меблі/драбину/коробки.";
            } else if (total < 35 && Math.max(Math.max(w,h),dep) < 0.45) {
                r.suggestedType = ReviewIssue.SuggestedType.SCAN_ARTIFACT;
                r.confidence = 0.70;
                r.reason = "Малий ізольований кластер з малою кількістю depth-точок.";
            } else {
                r.suggestedType = ReviewIssue.SuggestedType.UNKNOWN;
                r.confidence = 0.52;
                r.reason = "Окремий внутрішній об'єм. Потрібне ручне підтвердження.";
            }
            out.add(r);
        }

        // Largest first; review is more useful this way.
        java.util.Collections.sort(out, (a,c) ->
                Double.compare(c.widthM()*c.heightM()*c.depthM(),
                        a.widthM()*a.heightM()*a.depthM()));
        if (out.size() > 30) return new ArrayList<>(out.subList(0,30));
        return out;
    }

    private void enqueue(LongCellMap cells, ArrayDeque<Cell> q, int x, int y, int z) {
        Cell n = cells.get(pack(x,y,z));
        if (n == null || n.visited) return;
        // A one-hit voxel is not reliable object occupancy. Previously sparse cells were skipped
        // only when they happened to be visited as an outer-loop seed. If a dense component was
        // traversed first, enqueue() still admitted those one-hit cells, making the result depend
        // on insertion order and allowing a chain of depth speckles to bridge two separate pieces
        // of furniture into one giant object. Keep the same >=2 evidence rule on every traversal
        // edge. Mark rejected sparse cells consumed so they cannot become later component seeds.
        if (!reliable(n)) { n.visited=true; return; }
        n.visited=true;
        q.addLast(n);
    }

    /**
     * A voxel needs both repeated occupancy and enough fused measurement quality. Requiring two
     * samples alone is insufficient because two low-confidence voxels can still be depth haze.
     * The 0.55 sum is deliberately permissive: two ordinary ~0.30 confidence confirmed voxels
     * survive, while pairs sitting near the confidence floor do not become topology bridges.
     */
    private boolean reliable(Cell c) {
        return c != null && c.count >= 2 && c.confidenceSum >= 0.55;
    }

    private long pack(int x,int y,int z) {
        long mask=0x1FFFFFL;
        return ((long)x & mask)<<42 | (((long)y & mask)<<21) | ((long)z & mask);
    }
}
